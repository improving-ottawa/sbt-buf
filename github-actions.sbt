inThisBuild(
  Seq(
    githubWorkflowBuild := Seq(WorkflowStep.Sbt(List("test", "scripted"))),
    githubWorkflowPublishTargetBranches := Seq() // manual publish/release process for the time being
  )
)
