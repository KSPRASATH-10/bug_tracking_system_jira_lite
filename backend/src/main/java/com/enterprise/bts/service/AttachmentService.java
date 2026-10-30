package com.enterprise.bts.service;

import com.enterprise.bts.config.BtsProperties;
import com.enterprise.bts.domain.Attachment;
import com.enterprise.bts.domain.Bug;
import com.enterprise.bts.domain.BugHistory;
import com.enterprise.bts.domain.HistoryEventType;
import com.enterprise.bts.domain.ProjectRole;
import com.enterprise.bts.domain.User;
import com.enterprise.bts.exception.FileRejectedException;
import com.enterprise.bts.exception.NotFoundException;
import com.enterprise.bts.repository.AttachmentRepository;
import com.enterprise.bts.repository.BugHistoryRepository;
import com.enterprise.bts.repository.BugRepository;
import com.enterprise.bts.repository.UserRepository;
import com.enterprise.bts.security.AppUserPrincipal;
import com.enterprise.bts.security.ProjectSecurity;
import com.enterprise.bts.service.FileValidationService.ValidatedUpload;
import com.enterprise.bts.web.dto.AttachmentResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Stores validated uploads on the local filesystem, outside any web-served directory, under opaque UUID
 * names that are never derived from user input. The original filename lives only in the database. If
 * the surrounding transaction fails to commit, the freshly written file is removed again.
 */
@Service
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);
    private static final int MAX_ATTACHMENTS_PER_BUG = 25;

    /** Everything the controller needs to stream a download. */
    public record AttachmentDownload(String filename, String contentType, long sizeBytes, Resource resource) {
    }

    private final Path root;
    private final BugRepository bugRepository;
    private final AttachmentRepository attachmentRepository;
    private final BugHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final FileValidationService validationService;
    private final ProjectSecurity projectSecurity;
    private final AuditService auditService;

    public AttachmentService(BtsProperties properties, BugRepository bugRepository,
                             AttachmentRepository attachmentRepository, BugHistoryRepository historyRepository,
                             UserRepository userRepository, FileValidationService validationService,
                             ProjectSecurity projectSecurity, AuditService auditService) {
        this.root = Paths.get(properties.storage().uploadDir()).toAbsolutePath().normalize();
        this.bugRepository = bugRepository;
        this.attachmentRepository = attachmentRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.validationService = validationService;
        this.projectSecurity = projectSecurity;
        this.auditService = auditService;
        createSecureDirectory(root);
    }

    @Transactional
    public AttachmentResponse upload(UUID projectId, UUID bugId, MultipartFile file, AppUserPrincipal caller) {
        if (file == null || file.isEmpty()) {
            throw new FileRejectedException("No file was provided");
        }
        Bug bug = bugRepository.findActiveInProject(bugId, projectId)
                .orElseThrow(() -> new NotFoundException("Bug not found"));
        if (bug.getProject().isArchived()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project is archived and read-only");
        }
        if (attachmentRepository.findByBugIdAndArchivedFalseOrderByCreatedAtAsc(bugId).size() >= MAX_ATTACHMENTS_PER_BUG) {
            throw new FileRejectedException("This bug already has the maximum number of attachments");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new FileRejectedException("The uploaded file could not be read");
        }
        ValidatedUpload upload = validationService.validate(file.getOriginalFilename(), file.getContentType(), content);

        UUID storageName = UUID.randomUUID();
        Path target = resolveStoragePath(storageName);
        writeFile(target, upload.content());
        deleteFileIfTransactionFails(target);

        User uploader = userRepository.findById(caller.getId())
                .orElseThrow(() -> new AccessDeniedException("Account no longer exists"));
        Attachment attachment = attachmentRepository.saveAndFlush(new Attachment(bugId, uploader, upload.filename(),
                storageName, upload.contentType(), upload.content().length, upload.sha256()));
        historyRepository.save(new BugHistory(bugId, uploader, HistoryEventType.ATTACHMENT_ADDED, "attachment",
                null, upload.filename()));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("filename", upload.filename());
        details.put("contentType", upload.contentType());
        details.put("sizeBytes", upload.content().length);
        details.put("sha256", upload.sha256());
        auditService.record("ATTACHMENT_ADDED", "ATTACHMENT", attachment.getId(), details);
        return AttachmentResponse.from(attachment);
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> list(UUID projectId, UUID bugId) {
        bugRepository.findActiveInProject(bugId, projectId)
                .orElseThrow(() -> new NotFoundException("Bug not found"));
        return attachmentRepository.findByBugIdAndArchivedFalseOrderByCreatedAtAsc(bugId).stream()
                .map(AttachmentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AttachmentDownload download(UUID projectId, UUID bugId, UUID attachmentId) {
        bugRepository.findActiveInProject(bugId, projectId)
                .orElseThrow(() -> new NotFoundException("Bug not found"));
        Attachment attachment = attachmentRepository.findActiveInBug(attachmentId, bugId)
                .orElseThrow(() -> new NotFoundException("Attachment not found"));
        Path path = resolveStoragePath(attachment.getStorageName());
        if (!Files.isRegularFile(path)) {
            log.error("Attachment {} has metadata but its file is missing from storage", attachmentId);
            throw new NotFoundException("Attachment content is unavailable");
        }
        return new AttachmentDownload(attachment.getOriginalFilename(), attachment.getContentType(),
                attachment.getSizeBytes(), new FileSystemResource(path));
    }

    /** Soft archive: the metadata row and the bytes are retained; the file simply stops being listed or served. */
    @Transactional
    public void archive(UUID projectId, UUID bugId, UUID attachmentId, AppUserPrincipal caller) {
        Bug bug = bugRepository.findActiveInProject(bugId, projectId)
                .orElseThrow(() -> new NotFoundException("Bug not found"));
        if (bug.getProject().isArchived()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project is archived and read-only");
        }
        Attachment attachment = attachmentRepository.findActiveInBug(attachmentId, bugId)
                .orElseThrow(() -> new NotFoundException("Attachment not found"));

        ProjectRole role = projectSecurity.roleOf(projectId, caller.getId()).orElse(null);
        boolean uploader = attachment.getUploader().getId().equals(caller.getId());
        if (!caller.isAdministrator() && role != ProjectRole.PROJECT_LEAD && !uploader) {
            throw new AccessDeniedException("Only the uploader or a project lead can remove an attachment");
        }
        User actor = userRepository.findById(caller.getId())
                .orElseThrow(() -> new AccessDeniedException("Account no longer exists"));
        attachment.archive();
        attachmentRepository.saveAndFlush(attachment);
        historyRepository.save(new BugHistory(bugId, actor, HistoryEventType.ARCHIVED, "attachment",
                attachment.getOriginalFilename(), null));
        auditService.record("ATTACHMENT_ARCHIVED", "ATTACHMENT", attachmentId,
                Map.of("filename", attachment.getOriginalFilename()));
    }

    // ------------------------------------------------------------------ storage helpers

    private Path resolveStoragePath(UUID storageName) {
        Path path = root.resolve(storageName.toString()).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalStateException("Resolved storage path escapes the upload directory");
        }
        return path;
    }

    private static void writeFile(Path target, byte[] content) {
        try {
            Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException | IOException ignored) {
                // Non-POSIX filesystem (e.g. Windows): ACL defaults apply.
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store the uploaded file", e);
        }
    }

    private static void deleteFileIfTransactionFails(Path target) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try {
                        Files.deleteIfExists(target);
                    } catch (IOException e) {
                        log.warn("Could not remove orphaned upload {}", target.getFileName());
                    }
                }
            }
        });
    }

    private static void createSecureDirectory(Path directory) {
        try {
            try {
                Files.createDirectories(directory,
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            } catch (UnsupportedOperationException e) {
                Files.createDirectories(directory);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create upload directory " + directory, e);
        }
    }
}
