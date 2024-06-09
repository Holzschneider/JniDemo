import org.gradle.configurationcache.extensions.capitalized

plugins {
    id("cpp-library")
    id("java-library")
}

group = "de.dualuse"
version = "1.0"

library {
    baseName.set(project.name.replace(Regex("\\W"), ""))

    privateHeaders.from(File(layout.buildDirectory.get().asFile, "generated/sources/headers/java/main/"))


    source.from(file("src/main/cpp"))
    linkage.set(listOf(Linkage.SHARED))

    targetMachines = listOf(
        machines.windows.x86_64,
        machines.macOS.x86_64, machines.macOS.architecture("aarch64"),
        machines.linux.x86_64//, machines.linux.architecture("aarch64") //, machines.macOS.architecture("x86-64-with-SSE3"),
    )


    binaries.configureEach( CppBinary::class ) {

        //map of gradle's operating system family to jni include directory
        val jniIncludesForOsMap = mapOf(
            machines.macOS.operatingSystemFamily.name to "darwin",
            machines.linux.operatingSystemFamily.name to "linux",
            machines.windows.operatingSystemFamily.name to "win32"
        );
        val jniOS = jniIncludesForOsMap[targetPlatform.targetMachine.operatingSystemFamily.name]!!;
        val jniIncludes = File(org.gradle.internal.jvm.Jvm.current().javaHome,"include");

//        println("binary ${this.name} is optimized: ${this.isOptimized}");

        with(this.compileTask.get()) {
            includes.from( jniIncludes )
            includes.from( File(jniIncludes, jniOS) )
            isPositionIndependentCode = true;

//            macros.put("NDEBUG", null)
//            compilerArgs.add("-W3")

            compilerArgs.addAll( this.toolChain.map {
                when (it) {
                    is Gcc, is Clang -> listOf("-std=c++20")
                    is VisualCpp -> listOf("/std:c++20")
                    else -> emptyList()
                }
            });

            if (this.isOptimized)
                compilerArgs.addAll(this.toolChain.map {
                    when (it) {
                        is Gcc, is Clang -> listOf("-O3","-ffast-math","-ftree-vectorize")
                        is VisualCpp -> listOf("/O2", "/fp:fast")
                        else -> emptyList()
                    }
                });

            //this compile task depends on java headers being generated before
            dependsOn( tasks.compileJava.get().path )
        }
    }
}

///depending on the library configuration, LinkSharedLibrary Tasks for each variant and dimension get created
tasks.withType(LinkSharedLibrary::class) {
    val linkerTask = this;

    afterEvaluate {
        ///if configured with target machines, linked file encodes variant information into the path:
        ///e.g. 'build/lib/main/debug/macos/x86-64/lib<project-name>.dylib'
        val linkerOutput = linkedFile.get().asFile;
        val archName = linkerOutput.parentFile.name;
        val osName = linkerOutput.parentFile.parentFile.name;
        val variantName = linkerOutput.parentFile.parentFile.parentFile.name
        val linkedRoot = linkerOutput.parentFile.parentFile.parentFile.parentFile;
        val cap = String::capitalized

        val configName = "${cap(variantName)}${cap(osName)}${cap(archName)}";

        ///create
        tasks.create("jar$configName", Jar::class) {
            dependsOn(linkerTask.path);
            from(File(linkedRoot,variantName)).include("${osName}/${archName}/*")
            archiveFileName.set("${project.name}-${project.version}-${osName}-${archName}-${variantName}.jar")
            destinationDirectory.set( File(project.layout.buildDirectory.get().asFile, "libs") )

//            tasks.jar.get().dependsOn(this.path)
            tasks.classes.get().dependsOn(this.path)
        }
    }
}


fun javaImplementation(vararg dependencies: Any) = dependencies.forEach {
    dependencies {
        compileOnly(it)
        runtimeOnly(it)
        testCompileOnly(it)
        testRuntimeOnly(it)
    }
}



repositories {
    mavenCentral()
}

dependencies {
    runtimeOnly( fileTree("build/libs") { include("*${"-${project.version}-"}*${properties["buildType"]}.jar") } )

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}


tasks.jar {
    this.manifest {

    }
    this.metaInf {
        from("src/main/meta")
        include("*/**");
    }
}

tasks.test {
    useJUnitPlatform()
}