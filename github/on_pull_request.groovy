/*
  Copyright © 2018 Booz Allen Hamilton. All Rights Reserved.
  This software package is licensed under the Booz Allen Public License. The license can be found in the License file or at http://boozallen.github.io/licenses/bapl
*/

import org.kohsuke.github.GitHub

void call(Map args = [:], body){
  
  // do nothing if not pr
  if (!env.GIT_BUILD_CAUSE.equals("pr")) 
    return
  
  def source_branch = get_source_branch()
  def target_branch = env.CHANGE_TARGET
    
  // do nothing in source branch doesn't match
  if (args.from)
  if (!(source_branch ==~ (~args.from) ))// convert string to regex
    return

  // do nothing if target branch doesnt match
  if (args.to)
  if (!(target_branch ==~ (~args.to) ))// convert string to regex
    return
  
  println "running because of a PR from ${source_branch} to ${target_branch}"
  body()  

}

def get_source_branch(){

  String ghUrl = config.enterprise ? "${env.GIT_URL.split("/")[0..-3].join("/")}/api/v3" : "https://api.github.com"
  def repo
  def org

  def cred_id = env.GIT_CREDENTIAL_ID

  withCredentials([usernamePassword(credentialsId: cred_id, passwordVariable: 'PAT', usernameVariable: 'USER')]) {
    if( config.enterprise ){
      return GitHub.connectToEnterprise(ghUrl, PAT).getRepository("${env.ORG_NAME}/${env.REPO_NAME}")
              .getPullRequest(env.CHANGE_ID.toInteger())
              .getHead()
              .getRef()
    } else {

      return GitHub.connectUsingOAuth(PAT).
              getRepository("${env.ORG_NAME}/${env.REPO_NAME}")
              .getPullRequest(env.CHANGE_ID.toInteger())
              .getHead()
              .getRef()
    }
  }
}
