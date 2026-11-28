/*
 * Copyright 2020-2024 Dr (dr@der.kim) and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/deng-rui/RW-HPS/blob/master/LICENSE
 */

dependencies {
    compileOnlyAndTest("org.ow2.asm:asm:9.7")
    compileOnlyAndTest("org.ow2.asm:asm-tree:9.7")
}

tasks.jar {
    project.makeDependTree()
}

// The desktop client has a separate JVM. Keep this opt-in Java 8 agent out of Server-All.
val clientResyncAgent by sourceSets.creating
val clientResyncAgentTest by sourceSets.creating
val clientResyncVerificationRuntime by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}

dependencies {
    add(clientResyncAgent.implementationConfigurationName, "org.ow2.asm:asm:9.7")
    add(clientResyncAgent.implementationConfigurationName, "org.ow2.asm:asm-tree:9.7")
    add(clientResyncAgentTest.implementationConfigurationName, clientResyncAgent.output)
    // Only the verifier requires the unused Android types omitted from game-lib.jar.
    add(clientResyncVerificationRuntime.name, "com.google.android:android:4.1.1.4")
}

tasks.named<JavaCompile>(clientResyncAgent.compileJavaTaskName) { options.release.set(8) }
tasks.named<JavaCompile>(clientResyncAgentTest.compileJavaTaskName) { options.release.set(8) }

val clientResyncAgentJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Build the opt-in desktop client resync agent"
    archiveFileName.set("cnkd-client-resync-agent.jar")
    from(clientResyncAgent.output)
    from({ configurations[clientResyncAgent.runtimeClasspathConfigurationName].map { zipTree(it) } }) {
        include("org/objectweb/asm/**", "META-INF/LICENSE*")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    manifest.attributes("Premain-Class" to "net.rwhps.asm.clientresync.ClientResyncAgent")
}

tasks.register<JavaExec>("clientResyncAgentCheck") {
    group = "verification"
    description = "Verify the agent against the supplied engine classes and test the resync gate"
    dependsOn(clientResyncAgentJar)
    val engine = rootProject.file("Server-Core/libs/game-lib.jar")
    classpath = files(clientResyncAgentTest.output, engine, rootProject.file("Server-Core/libs/slick.jar")) +
            clientResyncVerificationRuntime
    mainClass.set("net.rwhps.asm.clientresync.ResyncAgentChecks")
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-Xverify:all", "-javaagent:${clientResyncAgentJar.get().archiveFile.get().asFile}")
    })
}

fun DependencyHandler.compileOnlyAndTest(dependencyNotation: Any) {
    this.testImplementation(dependencyNotation)
    this.compileOnly(dependencyNotation)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.github.RW-HPS"
            artifactId = "ASM-Framework"
            description = "Dedicated to Rusted Warfare(RustedWarfare) High Performance Server"

            from(components.getByName("java"))

            pom {
                scm {
                    url.set("https://github.com/deng-rui/RW-HPS")
                    connection.set("scm:https://github.com/deng-rui/RW-HPS.git")
                    developerConnection.set("scm:git@github.com:deng-rui/RW-HPS.git")
                }

                licenses {
                    license {
                        name.set("GNU AGPLv3")
                        url.set("https://github.com/deng-rui/RW-HPS/blob/master/LICENSE")
                    }
                }

                developers {
                    developer {
                        id.set("RW-HPS")
                        name.set("RW-HPS Technologies")
                    }
                }

            }

            pom.withXml {
                val root = asNode()
                root.appendNode("description", project.description)
                root.appendNode("name", project.name)
                root.appendNode("url", "https://github.com/deng-rui/RW-HPS")
            }
        }
    }
}