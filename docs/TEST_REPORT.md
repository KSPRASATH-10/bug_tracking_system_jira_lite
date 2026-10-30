BUG TRACKING SYSTEM JIRA LITE TEST REPORT

PS C:\Projects\IOC\bug_tracking_system_jira_lite\backend> mvn clean test
[INFO] Scanning for projects...
[INFO] 
[INFO] ----------------------------< com.bny:bts >-----------------------------
[INFO] Building enterprise-jira-lite-backend 1.0.0
[INFO]   from pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- clean:3.3.2:clean (default-clean) @ bts ---
[INFO] Deleting C:\Projects\IOC\bug_tracking_system_jira_lite\backend\target
[INFO] 
[INFO] --- resources:3.3.1:resources (default-resources) @ bts ---
[INFO] Copying 1 resource from src\main\resources to target\classes
[INFO] Copying 2 resources from src\main\resources to target\classes
[INFO] 
[INFO] --- compiler:3.13.0:compile (default-compile) @ bts ---
[INFO] Recompiling the module because of changed source code.
[INFO] Compiling 77 source files with javac [debug parameters release 21] to target\classes
[WARNING] unknown enum constant javax.annotation.meta.When.MAYBE
  reason: class file for javax.annotation.meta.When not found
[INFO] 
[INFO] --- resources:3.3.1:testResources (default-testResources) @ bts ---
[INFO] Copying 1 resource from src\test\resources to target\test-classes
[INFO] 
[INFO] --- compiler:3.13.0:testCompile (default-testCompile) @ bts ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 10 source files with javac [debug parameters release 21] to target\test-classes
[INFO] 
[INFO] --- surefire:3.2.5:test (default-test) @ bts ---
[INFO] Using auto detected provider org.apache.maven.surefire.junitplatform.JUnitPlatformProvider
[INFO] 
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.enterprise.bts.integration.AuthIntegrationTest
04:51:34.456 [main] INFO org.springframework.test.context.support.AnnotationConfigContextLoaderUtils -- Could not detect default configuration classes for test class [com.enterprise.bts.integration.AuthIntegrationTest]: AuthIntegrationTest does not declare any static, non-private, non-final, nested classes annotated with @Configuration.
04:51:34.594 [main] INFO org.springframework.boot.test.context.SpringBootTestContextBootstrapper -- Found @SpringBootConfiguration com.enterprise.bts.BtsApplication for test class com.enterprise.bts.integration.AuthIntegrationTest
04:51:34.773 [main] INFO org.testcontainers.images.PullPolicy -- Image pull policy will be performed by: DefaultPullPolicy()
04:51:34.776 [main] INFO org.testcontainers.utility.ImageNameSubstitutor -- Image name substitution will be performed by: DefaultImageNameSubstitutor (composite of 'ConfigurationFileImageNameSubstitutor' and 'PrefixingImageNameSubstitutor')
04:51:34.825 [main] INFO org.testcontainers.DockerClientFactory -- Testcontainers version: 1.21.4
04:51:35.163 [main] INFO org.testcontainers.dockerclient.DockerClientProviderStrategy -- Loaded org.testcontainers.dockerclient.NpipeSocketClientProviderStrategy from ~/.testcontainers.properties, will try it first
04:51:35.796 [main] INFO org.testcontainers.dockerclient.DockerClientProviderStrategy -- Found Docker environment with localNpipe socket (npipe:////./pipe/docker_engine)
04:51:35.798 [main] INFO org.testcontainers.DockerClientFactory -- Docker host IP address is localhost
04:51:35.823 [main] INFO org.testcontainers.DockerClientFactory -- Connected to docker: 
  Server Version: 29.9.2
  API Version: 1.56
  Operating System: Docker Desktop
  Total Memory: 7805 MB
  Labels: 
    com.docker.desktop.address=npipe://\\.\pipe\docker_cli
04:51:35.981 [main] INFO tc.testcontainers/ryuk:0.12.0 -- Creating container for image: testcontainers/ryuk:0.12.0
04:51:36.138 [main] INFO org.testcontainers.utility.RegistryAuthLocator -- Credential helper/store (docker-credential-desktop) does not have credentials for https://index.docker.io/v1/
04:51:36.286 [main] INFO tc.testcontainers/ryuk:0.12.0 -- Container testcontainers/ryuk:0.12.0 is starting: df3796b4ed61e70fb36531af7a4ddf4dcad84bd53deb22f28d1ca385a941ba67
04:51:36.853 [main] INFO tc.testcontainers/ryuk:0.12.0 -- Container testcontainers/ryuk:0.12.0 started in PT0.8723882S
04:51:36.890 [main] INFO org.testcontainers.utility.RyukResourceReaper -- Ryuk started - will monitor and terminate Testcontainers containers on JVM exit
04:51:36.890 [main] INFO org.testcontainers.DockerClientFactory -- Checking the system...
04:51:36.891 [main] INFO org.testcontainers.DockerClientFactory -- ?? Docker server version should be at least 1.6.0
04:51:36.894 [main] INFO tc.postgres:16-alpine -- Creating container for image: postgres:16-alpine
04:51:37.048 [main] INFO tc.postgres:16-alpine -- Container postgres:16-alpine is starting: 0ee7906d078d62dff8a4e4f718b0ce0ad3ed41e5511ba7ce2f8aad1fabc649d9
04:51:40.258 [main] INFO tc.postgres:16-alpine -- Container postgres:16-alpine started in PT3.363363S
04:51:40.258 [main] INFO tc.postgres:16-alpine -- Container is started (JDBC URL: jdbc:postgresql://localhost:65342/bts?loggerLevel=OFF)

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

 :: Spring Boot ::                (v3.3.5)

2026-10-30T04:51:40.756Z  INFO [cid=none] 15416 --- [enterprise-jira-lite] [           main] c.e.bts.integration.AuthIntegrationTest  : Starting AuthIntegrationTest using Java 21.0.12.1 with PID 15416 (started by sakth in C:\Projects\IOC\bug_tracking_system_jira_lite\backend)
2026-10-30T04:51:40.757Z  INFO [cid=none] 15416 --- [enterprise-jira-lite] [           main] c.e.bts.integration.AuthIntegrationTest  : The following 1 profile is active: "test"
2026-10-30T04:51:53.315Z  INFO [cid=none] 15416 --- [enterprise-jira-lite] [           main] c.e.bts.integration.AuthIntegrationTest  : Started AuthIntegrationTest in 13.007 seconds (process running for 19.906)
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
[INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 27.55 s -- in com.enterprise.bts.integration.AuthIntegrationTest
[INFO] Running com.enterprise.bts.integration.BolaSecurityIntegrationTest
2026-10-30T04:52:02.133Z  WARN [cid=b8820b17-d52e-4669-8cb6-b73fa2b62492] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: PUT /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/575236c8-f9c6-490a-be34-04c20cadb637/assignee
2026-10-30T04:52:02.150Z  WARN [cid=78b9d912-e109-4f01-9c90-321de4016b54] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: DELETE /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/575236c8-f9c6-490a-be34-04c20cadb637
2026-10-30T04:52:02.169Z  WARN [cid=8fed1835-a49a-4b05-bfe2-5321672afc57] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: PUT /api/projects/20000000-0000-4000-8000-0000000000a1/members
2026-10-30T04:52:02.188Z  WARN [cid=06b26cf0-b2cd-4618-bc8e-d95c894dbfed] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: DELETE /api/projects/20000000-0000-4000-8000-0000000000a1/members/00000000-0000-4000-8000-000000000004
2026-10-30T04:52:02.202Z  WARN [cid=ffa052bd-c7ff-4164-a0cf-703c9c57543f] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: DELETE /api/projects/20000000-0000-4000-8000-0000000000a1
2026-10-30T04:52:02.618Z  WARN [cid=f77cb3a6-d496-4599-a059-4736f65add33] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000b1
2026-10-30T04:52:02.646Z  WARN [cid=5d10db13-6bfc-4b0d-8a38-4508e8095a4b] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000b1/members
2026-10-30T04:52:02.704Z  WARN [cid=78f4ff42-523e-443a-a35d-4d5c3a897eba] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: PUT /api/projects/20000000-0000-4000-8000-0000000000b1/members
2026-10-30T04:52:02.728Z  WARN [cid=048471f1-8f82-4d47-9c0c-a15fc35bf74a] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: DELETE /api/projects/20000000-0000-4000-8000-0000000000b1/members/4c214b0d-f2b2-44ff-823b-4320b7b33b15
2026-10-30T04:52:02.747Z  WARN [cid=28fc6239-4799-470c-a37d-baafb1080209] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: DELETE /api/projects/20000000-0000-4000-8000-0000000000b1
2026-10-30T04:52:02.779Z  WARN [cid=aa306740-50b5-479f-b6b9-e35e1b8c4343] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000b1/bugs
2026-10-30T04:52:02.805Z  WARN [cid=e64f08f1-3208-4933-af45-484e0c453cff] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: POST /api/projects/20000000-0000-4000-8000-0000000000b1/bugs
2026-10-30T04:52:02.826Z  WARN [cid=68ebcda0-36a7-49a9-890d-997c460524e9] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000b1/bugs/30000000-0000-4000-8000-0000000000b1
2026-10-30T04:52:02.914Z  WARN [cid=f8eb283f-2d5a-4b56-9013-639ec4f3db28] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: PATCH /api/projects/20000000-0000-4000-8000-0000000000b1/bugs/30000000-0000-4000-8000-0000000000b1
2026-10-30T04:52:02.966Z  WARN [cid=e7e427ae-29f9-4583-bb68-f80cba88005b] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: POST /api/projects/20000000-0000-4000-8000-0000000000b1/bugs/30000000-0000-4000-8000-0000000000b1/transition
2026-10-30T04:52:03.022Z  WARN [cid=b1b15d73-bca0-441a-997e-fe1c1a57d392] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: PUT /api/projects/20000000-0000-4000-8000-0000000000b1/bugs/30000000-0000-4000-8000-0000000000b1/assignee
2026-10-30T04:52:03.046Z  WARN [cid=768c791e-e51d-442d-a34c-3e1ab1da4a4f] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: DELETE /api/projects/20000000-0000-4000-8000-0000000000b1/bugs/30000000-0000-4000-8000-0000000000b1
2026-10-30T04:52:03.078Z  WARN [cid=ebb525bd-a986-4cc9-8f67-636342846a29] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000b1/bugs/30000000-0000-4000-8000-0000000000b1/timeline
2026-10-30T04:52:03.115Z  WARN [cid=a6575ae8-f5cf-466d-abb4-fd6fa97082fa] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000b1/bugs/30000000-0000-4000-8000-0000000000b1/comments
2026-10-30T04:52:03.138Z  WARN [cid=d296f886-f42d-48ad-b6ef-c420fb628205] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: POST /api/projects/20000000-0000-4000-8000-0000000000b1/bugs/30000000-0000-4000-8000-0000000000b1/comments
2026-10-30T04:52:03.162Z  WARN [cid=02dbf708-e11d-4250-b012-e7ad6a14674f] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000b1/bugs/30000000-0000-4000-8000-0000000000b1/attachments
2026-10-30T04:52:03.185Z  WARN [cid=1f18b66a-be84-4b17-b091-ee9c52321613] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: POST /api/projects/20000000-0000-4000-8000-0000000000b1/bugs/30000000-0000-4000-8000-0000000000b1/attachments
2026-10-30T04:52:03.205Z  WARN [cid=b80576de-877e-48cf-87d9-213cfcb36aaa] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000b1/bugs/30000000-0000-4000-8000-0000000000b1/attachments/4820cf46-f46a-4b43-83cb-c54c53ed5612/content
2026-10-30T04:52:03.231Z  WARN [cid=ce852dc1-b906-4c28-8b80-6ab6856a727f] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: DELETE /api/projects/20000000-0000-4000-8000-0000000000b1/bugs/30000000-0000-4000-8000-0000000000b1/attachments/8750f192-2454-46c2-94be-409f4259dbdd
2026-10-30T04:52:03.762Z  WARN [cid=4d6cf971-ca74-42b5-8f0b-b029e1fe1ccd] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: POST /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/cbb261c2-8e41-4ecd-8b79-57b9b03e36a3/transition
2026-10-30T04:52:03.774Z  WARN [cid=3b10c04e-a656-4bb7-9180-a933eabacb2c] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: PUT /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/cbb261c2-8e41-4ecd-8b79-57b9b03e36a3/assignee
2026-10-30T04:52:03.788Z  WARN [cid=fdd75035-2de2-434b-93d9-0f80079f8378] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: DELETE /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/cbb261c2-8e41-4ecd-8b79-57b9b03e36a3
2026-10-30T04:52:03.805Z  WARN [cid=3cffc52c-cfe4-444c-bdd4-6e0015eead5a] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: PUT /api/projects/20000000-0000-4000-8000-0000000000a1/members
2026-10-30T04:52:03.817Z  WARN [cid=e86305b7-e271-46cb-a1c6-11dccd7563d0] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: DELETE /api/projects/20000000-0000-4000-8000-0000000000a1
2026-10-30T04:52:03.906Z  WARN [cid=c1a55f4f-2000-4d3b-a4c8-0af338a8dd22] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: PATCH /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/30000000-0000-4000-8000-0000000000a3
2026-10-30T04:52:05.128Z  WARN [cid=e90a8cfb-f035-4a29-a024-687d45b2873b] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: POST /api/projects
2026-10-30T04:52:05.139Z  WARN [cid=95c4ff54-62a2-4362-9f26-15a16c4f74f4] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: POST /api/projects
2026-10-30T04:52:05.148Z  WARN [cid=8c205034-6fe3-472a-8422-b3e6cdd0010d] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: POST /api/projects
2026-10-30T04:52:05.157Z  WARN [cid=09cc7463-9f02-4f2e-a27b-259cce6b2ad0] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: POST /api/projects
2026-10-30T04:52:05.177Z  WARN [cid=edbd9abd-2c7c-4cf7-8951-e779f7438535] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000b1
2026-10-30T04:52:05.196Z  WARN [cid=5d714a53-6c5e-4960-bf00-bcb27319aba8] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000b1/members
2026-10-30T04:52:05.220Z  WARN [cid=49572a39-6e23-4edb-b1e4-ea8775918931] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: PUT /api/projects/20000000-0000-4000-8000-0000000000b1/members
2026-10-30T04:52:05.241Z  WARN [cid=0949c451-9058-4e11-88ff-7261a3527ed1] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: DELETE /api/projects/20000000-0000-4000-8000-0000000000b1/members/b7a4a20a-49e6-445e-bb68-3d58530f4407
2026-10-30T04:52:05.267Z  WARN [cid=ab466d78-c741-4ba6-b519-a6886c4dbeb6] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: DELETE /api/projects/20000000-0000-4000-8000-0000000000b1
2026-10-30T04:52:05.293Z  WARN [cid=b807d4f8-bd71-41c9-9a36-12069f857e05] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000b1/bugs
2026-10-30T04:52:05.361Z  WARN [cid=42892686-1375-4aa1-85a4-78ccc1ab9e9c] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: POST /api/projects/20000000-0000-4000-8000-0000000000b1/bugs
2026-10-30T04:52:05.405Z  WARN [cid=47dcdad5-142d-4aca-a7cd-d456c1ea71c6] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000b1/bugs/30000000-0000-4000-8000-0000000000b1
2026-10-30T04:52:05.462Z  WARN [cid=c412281b-2b46-4ba3-9486-cecbb0d8a175] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: PATCH /api/projects/20000000-0000-4000-8000-0000000000b1/bugs/30000000-0000-4000-8000-0000000000b1
2026-10-30T04:52:05.745Z  WARN [cid=32ce51c1-90e4-4f78-877d-b789e2fc12f4] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: POST /api/projects/20000000-0000-4000-8000-0000000000b1/bugs/30000000-0000-4000-8000-0000000000b1/transition
2026-10-30T04:52:05.776Z  WARN [cid=d2785b23-3200-4fc0-aa59-97c7c6b1df6f] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: PUT /api/projects/20000000-0000-4000-8000-0000000000b1/bugs/30000000-0000-4000-8000-0000000000b1/assignee
2026-10-30T04:52:05.834Z  WARN [cid=6c4dc7d9-3e31-49c8-8269-b8d32fafb650] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: DELETE /api/projects/20000000-0000-4000-8000-0000000000b1/bugs/30000000-0000-4000-8000-0000000000b1
2026-10-30T04:52:05.854Z  WARN [cid=9fa356cb-5361-4ca0-b8bb-576a91e99655] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000b1/bugs/30000000-0000-4000-8000-0000000000b1/timeline
2026-10-30T04:52:05.875Z  WARN [cid=1e8b8200-5e07-43a7-9c48-e7196487d1b1] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000b1/bugs/30000000-0000-4000-8000-0000000000b1/comments
2026-10-30T04:52:05.897Z  WARN [cid=2a7fde65-742a-4aae-a78b-41054c22d306] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: POST /api/projects/20000000-0000-4000-8000-0000000000b1/bugs/30000000-0000-4000-8000-0000000000b1/comments
2026-10-30T04:52:05.927Z  WARN [cid=68b29aa8-de11-4e20-81e2-f6f0e357e689] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000b1/bugs/30000000-0000-4000-8000-0000000000b1/attachments
2026-10-30T04:52:05.946Z  WARN [cid=e00e5309-e1ee-4d53-aff6-00153b1557e3] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: POST /api/projects/20000000-0000-4000-8000-0000000000b1/bugs/30000000-0000-4000-8000-0000000000b1/attachments
2026-10-30T04:52:05.966Z  WARN [cid=74571a24-66bc-47cd-a051-f2717476cc98] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000b1/bugs/30000000-0000-4000-8000-0000000000b1/attachments/e9608665-90e1-4a9f-afce-147526a2bcdb/content
2026-10-30T04:52:05.984Z  WARN [cid=d6d107de-907e-4b3b-925b-848be8de8be5] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: DELETE /api/projects/20000000-0000-4000-8000-0000000000b1/bugs/30000000-0000-4000-8000-0000000000b1/attachments/e418cc7e-31d2-47ac-929c-eea372457e93
2026-10-30T04:52:06.125Z  WARN [cid=77a288cf-fbc1-429a-b223-c1fca79e7b63] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/e21a10a8-929d-42e1-8b5b-8a09c735f82d/attachments/7e7903d8-0083-4e62-bd11-20071ba3edfb/content
2026-10-30T04:52:06.354Z  WARN [cid=4aec73cc-a6d8-433f-a15d-d84cf3bddf7c] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000a1/bugs
2026-10-30T04:52:06.409Z  WARN [cid=58f56b0c-4e7c-41c5-9840-39103f6e24cd] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000a1
2026-10-30T04:52:06.425Z  WARN [cid=603b2269-8024-4a0c-9f51-a070fab03e8b] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000a1/members
2026-10-30T04:52:06.453Z  WARN [cid=95dda918-52ec-4702-b8b3-6076ad3c13af] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: PUT /api/projects/20000000-0000-4000-8000-0000000000a1/members
2026-10-30T04:52:06.466Z  WARN [cid=9342c970-c7b1-4469-a274-17e04e7a7747] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: DELETE /api/projects/20000000-0000-4000-8000-0000000000a1/members/d71e6574-df74-4e60-9e47-e9a55e958b97
2026-10-30T04:52:06.487Z  WARN [cid=5548b651-1f82-4f21-b344-b51b00be353a] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: DELETE /api/projects/20000000-0000-4000-8000-0000000000a1
2026-10-30T04:52:06.516Z  WARN [cid=4609f1ce-7681-4464-a038-7bda97946d74] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000a1/bugs
2026-10-30T04:52:06.547Z  WARN [cid=9664352e-f95a-4747-b0e6-670f9543efbd] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: POST /api/projects/20000000-0000-4000-8000-0000000000a1/bugs
2026-10-30T04:52:06.574Z  WARN [cid=9717abfd-f854-47dd-887d-09f06d4239ad] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/30000000-0000-4000-8000-0000000000a1
2026-10-30T04:52:06.593Z  WARN [cid=a9b9df10-b2d8-4cef-8fcc-0e6749a097d1] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: PATCH /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/30000000-0000-4000-8000-0000000000a1
2026-10-30T04:52:06.616Z  WARN [cid=cd85d4ca-f789-4ecf-967b-defa1bcacf36] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: POST /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/30000000-0000-4000-8000-0000000000a1/transition
2026-10-30T04:52:06.640Z  WARN [cid=780641fa-cb6d-4cf7-9a04-b35616f0b03a] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: PUT /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/30000000-0000-4000-8000-0000000000a1/assignee
2026-10-30T04:52:06.662Z  WARN [cid=1c55b419-aa58-48c7-8243-85beff5c6465] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: DELETE /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/30000000-0000-4000-8000-0000000000a1
2026-10-30T04:52:06.682Z  WARN [cid=16708eac-6278-45cb-97a0-993025c20c4a] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/30000000-0000-4000-8000-0000000000a1/timeline
2026-10-30T04:52:06.704Z  WARN [cid=f1e7008d-a55a-4734-91b4-e17a13515d1e] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/30000000-0000-4000-8000-0000000000a1/comments
2026-10-30T04:52:06.728Z  WARN [cid=611ff8fe-ce18-44f9-a897-5c019ded6da7] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: POST /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/30000000-0000-4000-8000-0000000000a1/comments
2026-10-30T04:52:06.749Z  WARN [cid=22c740f4-6b07-4f4e-b862-34bd2625b123] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/30000000-0000-4000-8000-0000000000a1/attachments
2026-10-30T04:52:06.771Z  WARN [cid=598740bd-c808-437a-b5ee-acf4ae24e4e9] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: POST /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/30000000-0000-4000-8000-0000000000a1/attachments
2026-10-30T04:52:06.793Z  WARN [cid=69fbf899-0ec0-45cc-ad29-94fe29de2730] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/30000000-0000-4000-8000-0000000000a1/attachments/ff102a29-5013-43b6-ac6e-25538fb163f7/content
2026-10-30T04:52:06.822Z  WARN [cid=d922046d-fdb6-46c6-8737-6efcbcd339fa] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: DELETE /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/30000000-0000-4000-8000-0000000000a1/attachments/cfdefd95-248c-45dc-988b-9739e9487b7f
2026-10-30T04:52:06.842Z  WARN [cid=0d8e2652-d79b-436d-b5e9-ca92c213affc] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000a1
2026-10-30T04:52:06.867Z  WARN [cid=da877717-cee7-44d4-8262-1b6db82b0000] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000a1/members
2026-10-30T04:52:06.893Z  WARN [cid=bac3973b-4972-4c59-8efc-ff6c5182fe53] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: PUT /api/projects/20000000-0000-4000-8000-0000000000a1/members
2026-10-30T04:52:06.950Z  WARN [cid=2d9ff9ae-9ebd-4890-bbd9-b881ce5333e0] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: DELETE /api/projects/20000000-0000-4000-8000-0000000000a1/members/695f1a19-d6fb-437e-a53f-843b7e119229
2026-10-30T04:52:07.004Z  WARN [cid=940e2bc4-e571-40ce-80fa-2e905c3aed4c] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: DELETE /api/projects/20000000-0000-4000-8000-0000000000a1
2026-10-30T04:52:07.046Z  WARN [cid=6ea6dda9-8f69-4ab1-aff6-5d8ab440c516] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000a1/bugs
2026-10-30T04:52:07.072Z  WARN [cid=59d0d929-a29b-43e6-94ed-fae29e9127f6] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: POST /api/projects/20000000-0000-4000-8000-0000000000a1/bugs
2026-10-30T04:52:07.114Z  WARN [cid=6c363c26-2569-4a28-93fe-97419ea81718] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/30000000-0000-4000-8000-0000000000a1
2026-10-30T04:52:07.160Z  WARN [cid=6ee1eb07-1456-40ba-995b-84de8ea1dce4] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: PATCH /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/30000000-0000-4000-8000-0000000000a1
2026-10-30T04:52:07.200Z  WARN [cid=56881ec4-7e22-4360-98cd-a8f62ead1eb8] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: POST /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/30000000-0000-4000-8000-0000000000a1/transition
2026-10-30T04:52:07.240Z  WARN [cid=e031bf49-715b-4c5d-b1e3-6865de5c817c] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: PUT /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/30000000-0000-4000-8000-0000000000a1/assignee
2026-10-30T04:52:07.275Z  WARN [cid=a84d4cc8-0c4f-4f09-a034-ec34bf485585] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: DELETE /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/30000000-0000-4000-8000-0000000000a1
2026-10-30T04:52:07.313Z  WARN [cid=3c9754a7-cd39-4318-896f-fb37ecd74945] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/30000000-0000-4000-8000-0000000000a1/timeline
2026-10-30T04:52:07.341Z  WARN [cid=9196f127-a2c4-4487-9312-7d6d749b93c9] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/30000000-0000-4000-8000-0000000000a1/comments
2026-10-30T04:52:07.366Z  WARN [cid=96de2821-b86a-4098-9186-d6d4cb232bab] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: POST /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/30000000-0000-4000-8000-0000000000a1/comments
2026-10-30T04:52:07.393Z  WARN [cid=5e09ad0f-3119-4270-93a6-b6beb6564601] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/30000000-0000-4000-8000-0000000000a1/attachments
2026-10-30T04:52:07.418Z  WARN [cid=93146ab1-1cab-4ba8-b3e0-24897bc636f8] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: POST /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/30000000-0000-4000-8000-0000000000a1/attachments
2026-10-30T04:52:07.441Z  WARN [cid=73f88346-bce7-4341-8cc3-601fd8bd3e38] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/30000000-0000-4000-8000-0000000000a1/attachments/26196d45-22da-4649-bd47-8596dbbfbd63/content
2026-10-30T04:52:07.501Z  WARN [cid=ca80b7a3-1d68-4a3d-986e-a5aa20c37dfc] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: DELETE /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/30000000-0000-4000-8000-0000000000a1/attachments/4849ec2f-cf7c-4f0d-b202-d892750bae9a
2026-10-30T04:52:07.538Z  WARN [cid=f43bf5ec-1523-4f46-a585-ddb54f4fd1fc] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/a225bf37-fa16-4326-a221-21b2af15e3ac/bugs
2026-10-30T04:52:07.561Z  WARN [cid=31e861fa-4c40-4bb8-8dc0-0fd84355872e] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000a1/bugs
[INFO] Tests run: 105, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 5.770 s -- in com.enterprise.bts.integration.BolaSecurityIntegrationTest
[INFO] Running com.enterprise.bts.integration.BugWorkflowIntegrationTest
2026-10-30T04:52:09.101Z  WARN [cid=10f3fd8a-bc4d-461c-bb3e-d0428bda6f60] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: DELETE /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/e12eef55-a9d2-4074-b089-b32609f167c9/attachments/64098977-c3b1-4688-b4e9-b928d71ae915
2026-10-30T04:52:09.323Z  WARN [cid=da8a6480-d9c9-416d-a610-514ae39601cc] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/0ed16158-5d7e-4b6b-b034-77d0cc350541
[INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.603 s -- in com.enterprise.bts.integration.BugWorkflowIntegrationTest
[INFO] Running com.enterprise.bts.integration.InjectionDefenseIntegrationTest
2026-10-30T04:52:11.878Z  WARN [cid=51b2e960-e513-46ad-990e-bfc8f3f193c2] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Access denied: GET /api/projects/20000000-0000-4000-8000-0000000000a1/bugs
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.409 s -- in com.enterprise.bts.integration.InjectionDefenseIntegrationTest
[INFO] Running com.enterprise.bts.integration.OptimisticLockingIntegrationTest
2026-10-30T04:52:13.549Z  INFO [cid=e2c51075-9b6d-45fd-9897-db22e7bb18b7] 15416 --- [enterprise-jira-lite] [pool-3-thread-2]c.e.bts.web.GlobalExceptionHandler       : Optimistic lock conflict on POST /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/40282f42-b39e-434b-8fec-b6c8e5278bb6/transition
2026-10-30T04:52:13.644Z  INFO [cid=d3880b21-f03a-4264-a577-ac806e8f114e] 15416 --- [enterprise-jira-lite] [pool-4-thread-2]c.e.bts.web.GlobalExceptionHandler       : Optimistic lock conflict on POST /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/f6423a74-ef09-4aac-97de-a43caf689938/transition
2026-10-30T04:52:13.726Z  INFO [cid=081d5418-d3de-4a7f-b92d-e80a24fd7451] 15416 --- [enterprise-jira-lite] [pool-5-thread-2]c.e.bts.web.GlobalExceptionHandler       : Optimistic lock conflict on POST /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/1ad4a218-eff3-474e-9656-517adb9f24c2/transition
2026-10-30T04:52:13.814Z  INFO [cid=69305467-a74f-4d55-b36a-fa49b2ca2363] 15416 --- [enterprise-jira-lite] [pool-6-thread-1]c.e.bts.web.GlobalExceptionHandler       : Optimistic lock conflict on POST /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/1018f999-93de-43e5-9745-49b9c9d32e8f/transition
2026-10-30T04:52:13.892Z  INFO [cid=58ab4cc5-3c0c-4f55-a964-ecbb84b477bf] 15416 --- [enterprise-jira-lite] [pool-7-thread-2]c.e.bts.web.GlobalExceptionHandler       : Optimistic lock conflict on POST /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/0a5dcff4-1076-451f-8068-308b65848ff1/transition
2026-10-30T04:52:13.978Z  INFO [cid=094bfdc4-0a1f-4728-aacf-539ed37063f6] 15416 --- [enterprise-jira-lite] [pool-8-thread-1]c.e.bts.web.GlobalExceptionHandler       : Optimistic lock conflict on POST /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/ae528244-fe53-420d-a3e2-636d15060002/transition
2026-10-30T04:52:14.074Z  INFO [cid=06d20db1-ad1d-400f-941a-3c37383942c4] 15416 --- [enterprise-jira-lite] [pool-9-thread-2]c.e.bts.web.GlobalExceptionHandler       : Optimistic lock conflict on PATCH /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/354dfa8c-e1ff-4487-bbbb-60c7b76652ec
2026-10-30T04:52:14.181Z  INFO [cid=2cb13e5e-fd6e-4a82-9a71-397901cd74e6] 15416 --- [enterprise-jira-lite] [ool-10-thread-2]c.e.bts.web.GlobalExceptionHandler       : Optimistic lock conflict on PATCH /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/af2155a7-65b6-4786-87ab-751883230739
2026-10-30T04:52:14.253Z  INFO [cid=aade7541-05ff-4570-8f43-6e57212d144e] 15416 --- [enterprise-jira-lite] [ool-11-thread-1]c.e.bts.web.GlobalExceptionHandler       : Optimistic lock conflict on POST /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/ecb5618d-d858-4098-85ba-05f6a01f608c/transition
2026-10-30T04:52:14.331Z  INFO [cid=576be69f-49e7-4f5b-8c6d-3d486cbbf7ac] 15416 --- [enterprise-jira-lite] [ool-12-thread-1]c.e.bts.web.GlobalExceptionHandler       : Optimistic lock conflict on POST /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/692c0cea-219b-491b-a8b4-c3216e68f9b6/transition
2026-10-30T04:52:14.419Z  INFO [cid=65581cd2-375b-4a62-8166-85d39ad8dedc] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Optimistic lock conflict on PATCH /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/c60d430e-327e-4bce-bc00-74babb696a4d
2026-10-30T04:52:14.433Z  INFO [cid=2dfac90b-e30c-4429-98df-5c727eaf1f75] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Optimistic lock conflict on POST /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/c60d430e-327e-4bce-bc00-74babb696a4d/transition
2026-10-30T04:52:14.449Z  INFO [cid=7f4c0e12-8529-4c0e-ba52-63f5e82264d2] 15416 --- [enterprise-jira-lite] [           main]c.e.bts.web.GlobalExceptionHandler       : Optimistic lock conflict on PUT /api/projects/20000000-0000-4000-8000-0000000000a1/bugs/c60d430e-327e-4bce-bc00-74babb696a4d/assignee
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.880 s -- in com.enterprise.bts.integration.OptimisticLockingIntegrationTest
[INFO] Running com.enterprise.bts.security.LoginRateLimitFilterTest
2026-10-30T04:52:14.854Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [           main] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.1
2026-10-30T04:52:15.169Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-2] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.172Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-3] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.172Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-4] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.172Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-5] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.173Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-6] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.179Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-7] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.179Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-8] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.183Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-9] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.184Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-9] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.185Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-9] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.187Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-9] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.187Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-6] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.188Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-6] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.169Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-1] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.189Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-11] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.182Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-8] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.190Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-11] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.190Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-8] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.190Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-11] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.191Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-11] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.191Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-14] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.192Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-14] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.192Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-16] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.193Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-4] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.188Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-1] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.194Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-4] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.194Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-4] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.194Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-4] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.194Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-4] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.194Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-4] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.195Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-6] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.191Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-8] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.196Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-8] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.197Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-8] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.197Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-4] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.197Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-8] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.197Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-4] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.197Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-8] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.198Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-10] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.198Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-12] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.200Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-12] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.200Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-12] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.200Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-12] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.200Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-6] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.200Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-12] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.201Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-12] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.201Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-12] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.201Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-12] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.201Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-12] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.202Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-6] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.203Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-7] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.203Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-6] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.203Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-7] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.203Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-6] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.203Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-7] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.203Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-6] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.204Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-6] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.204Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-3] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.191Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-13] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.204Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-13] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.204Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-3] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.192Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-15] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.206Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-15] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.206Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-15] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.206Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-15] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.207Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-15] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.207Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-15] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.207Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-3] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.207Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-15] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.198Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-10] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.208Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-10] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.208Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-10] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.208Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-10] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.209Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-10] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.209Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-10] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.209Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-10] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.199Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-16] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.210Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-16] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.210Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-16] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.210Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-16] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.210Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-16] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.210Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-16] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.211Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-16] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.200Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-5] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.193Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-14] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.193Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-1] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.204Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-11] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.205Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-2] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.206Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-13] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.208Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-3] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.209Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-10] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.214Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-9] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.215Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-4] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.216Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-8] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.216Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ool-13-thread-7] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.216Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-12] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.228Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [ol-13-thread-15] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.7
2026-10-30T04:52:15.281Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [           main] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.9
2026-10-30T04:52:15.313Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [           main] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.9
2026-10-30T04:52:15.314Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [           main] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.9
2026-10-30T04:52:15.368Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [           main] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.1
2026-10-30T04:52:15.371Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [           main] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.1
2026-10-30T04:52:15.372Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [           main] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.1
2026-10-30T04:52:15.373Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [           main] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.1
2026-10-30T04:52:15.375Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [           main] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.1
2026-10-30T04:52:15.375Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [           main] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.1
2026-10-30T04:52:15.375Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [           main] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.1
2026-10-30T04:52:15.441Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [           main] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.1
2026-10-30T04:52:15.515Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [           main] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.1
2026-10-30T04:52:15.544Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [           main] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.1
2026-10-30T04:52:15.550Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [           main] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.1
2026-10-30T04:52:15.551Z  WARN [cid=none] 15416 --- [enterprise-jira-lite] [           main] c.e.bts.security.LoginRateLimitFilter    : Login rate limit exceeded for client 10.0.0.1
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.066 s -- in com.enterprise.bts.security.LoginRateLimitFilterTest
[INFO] Running com.enterprise.bts.service.BugStateMachineTest
[INFO] Tests run: 61, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.175 s -- in com.enterprise.bts.service.BugStateMachineTest
[INFO] Running com.enterprise.bts.service.FileValidationServiceTest
[INFO] Tests run: 41, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.425 s -- in com.enterprise.bts.service.FileValidationServiceTest
[INFO] Running com.enterprise.bts.service.MarkdownSanitizerTest
[INFO] Tests run: 50, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.502 s -- in com.enterprise.bts.service.MarkdownSanitizerTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 329, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  52.603 s
[INFO] Finished at: 2026-10-30T10:22:17+05:30
[INFO] ------------------------------------------------------------------------
PS C:\Projects\IOC\bug_tracking_system_jira_lite\backend> 