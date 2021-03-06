import java.io.{File,IOException}

val rootDirectory = sys.props("user.dir")

lazy val dockerIp: String = {
  import scala.sys.process._

  // We only evaluate these commands if we're *running*. If we're just
  // compiling, we'll never run these commands.
  try {
    "docker-machine ip".!!.trim
  } catch {
    case _: IOException => {
      Seq("docker", "inspect", "-f", "{{ .NetworkSettings.Gateway }}", "overview-dev-database").!!.trim
    }
  }
}

val baseJavaOpts = Seq(
  "-Duser.timezone=UTC"
)

lazy val devJavaOpts = baseJavaOpts ++ Seq(
  s"-Ddb.default.properties.databaseName=overview-dev",
  s"-Ddb.default.properties.portNumber=9010",
  s"-Ddb.default.properties.serverName=$dockerIp",
  s"-Dredis.host=$dockerIp",
  s"-Dredis.port=9020",
  s"-DblobStorage.file.baseDirectory=$rootDirectory/blob-storage/dev",
  s"-Dsearch.baseDirectory=$rootDirectory/search/dev"
)

lazy val testJavaOpts = baseJavaOpts ++ Seq(
  s"-Ddb.default.properties.databaseName=overview-test",
  s"-Ddb.default.properties.portNumber=9010",
  s"-Ddb.default.properties.serverName=$dockerIp",
  s"-Dredis.host=$dockerIp",
  s"-Dredis.port=9020",
  s"-Dlogback.configurationFile=logback-test.xml",
  s"-DblobStorage.file.baseDirectory=$rootDirectory/blob-storage/test",
  s"-Dsearch.baseDirectory=$rootDirectory/search/test"
)

// Settings that apply to all our projects
lazy val root = (project in file("."))
  .settings(
    inThisBuild(List(
      // Settings common to all projects
      organization := "com.overviewdocs",
      version := "1.0.0-SNAPSHOT",
      scalaVersion := "2.12.4",
      scalacOptions := Seq("-deprecation", "-unchecked", "-feature", "-target:jvm-1.8", "-encoding", "UTF8"),
      resolvers := Seq(
        "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
        "jbig2 repository" at "http://jbig2-imageio.googlecode.com/svn/maven-repository",
        "Oracle Released Java Packages" at "http://download.oracle.com/maven",
        "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
      ),
      logBuffered := false,
      javaOptions in Compile := devJavaOpts,
      javaOptions in Test := testJavaOpts,
      fork := true, // so javaOptions gets set
      parallelExecution in Test := false, // because we test with the (global) database
      aggregate in Test := false, // otherwise we'd get parallel execution
      testOptions in Test := Seq(
        Tests.Argument(TestFrameworks.Specs2, "xonly"),
        Tests.Argument(TestFrameworks.Specs2, "showtimes"),
        Tests.Argument("junitxml", "console")
      )
    )),
    publish := (),
    publishLocal := ()
  )

// Runs database evolutions on prod+dev
lazy val `db-evolution-applier` = (project in file("db-evolution-applier"))
  .enablePlugins(JavaAppPackaging)
  .settings(
    target := baseDirectory.value / "target" / "db-evolution-applier", // [error] Overlapping output directories
    libraryDependencies ++= Dependencies.dbEvolutionApplierDependencies
  )

// Runs database evolutions on test (run this before running other tests)
lazy val `test-db-evolution-applier` = (project in file("db-evolution-applier"))
  .enablePlugins(JavaAppPackaging)
  .settings(
    target := baseDirectory.value / "target" / "test-db-evolution-applier", // [error] Overlapping output directories
    javaOptions in Compile := testJavaOpts,
    libraryDependencies ++= Dependencies.dbEvolutionApplierDependencies
  )

// Shared code for other projects
lazy val common = (project in file("common"))
  .enablePlugins(JavaAppPackaging)
  .settings(
    libraryDependencies ++= Dependencies.commonDependencies,
    sources in doc in Compile := List() // docs take time; skip 'em
  )

// Daemon that executes background tasks
lazy val worker = (project in file("worker"))
  .enablePlugins(JavaAppPackaging)
  //.enablePlugins(Revolver)
  .settings(
    libraryDependencies ++= Dependencies.workerDependencies,
    mainClass in Compile := Some("com.overviewdocs.Worker"),
    sources in doc in Compile := List(), // docs take time; skip 'em
    javaOptions in reStart ++= devJavaOpts
  )
  .dependsOn(common % "test->test;compile->compile")

// Daemon that communicates with web browsers
lazy val web = (project in file("web"))
  .enablePlugins(PlayScala)
  .enablePlugins(PlayNpm)
  .enablePlugins(SbtWeb)
  .enablePlugins(JavaAppPackaging)
  .settings(PlayNpm.projectSettings)
  .settings(
    PlayKeys.externalizeResources := false, // so `stage` doesn't nix all assets
    libraryDependencies ++= Dependencies.serverDependencies,
    TwirlKeys.templateImports ++= Seq("views.Magic._", "play.twirl.api.HtmlFormat"),
    routesImport += "extensions.Binders._",
    javaOptions in Test += "-Dlogger.resource=logback-test.xml",
    sources in doc in Compile := List(), // docs take time; skip 'em
    includeFilter in (Assets, digest) := new FileFilter {
      override def accept(f: File): Boolean = {
        val pathString = f.getPath.replaceAll(File.pathSeparator, "/")
        val rightDirectory = pathString.matches(".*/web/public/(fonts|images|javascript-bundles|javascripts/vendor|stylesheets)/.*")
        val rightExtension = f.getName.matches(".*\\.(css|js|png|jpg|ttf|woff|woff2|svg)")
        rightDirectory && rightExtension
      }
    },
    pipelineStages := Seq(digest, gzip)
  )
  .dependsOn(common % "test->test;compile->compile;test->compile")

// Test aggregator. Call as "./sbt all/test".
lazy val all = (project in file("all"))
  .aggregate(web, worker, common)
  .settings(
    test in Test := Def.sequential(
      (run in Runtime in `test-db-evolution-applier`).toTask(""),
      test in Test in common,
      test in Test in worker,
      test in Test in web
    ).value
  )
