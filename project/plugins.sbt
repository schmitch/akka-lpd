addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.0")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.6")

addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.2")
updateOptions := updateOptions.value.withGigahorse(false)