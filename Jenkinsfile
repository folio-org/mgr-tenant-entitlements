import org.folio.eurekaImage.EurekaImage
import org.jenkinsci.plugins.workflow.libs.Library

@Library('pipelines-shared-library@master') _
node('jenkins-agent-java17-bigmem') {
  stage('Build Docker Image') {
    dir('mgr-tenant-entitlements') {
      EurekaImage image = new EurekaImage(this)
      image.setModuleName('mgr-tenant-entitlements')
      image.makeImage()
    }
  }
}
buildMvn {
  publishModDescriptor = false
  mvnDeploy = true
  buildNode = 'jenkins-agent-java17-bigmem'
}
