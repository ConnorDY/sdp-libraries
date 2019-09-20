void call(app_env){
   stage("ECS: Deploy To ${app_env.long_name}"){


      String region = env.AWS_REGION ?: 
                     { error "must define AWS region where ecs cluster is location in ecs library." }() 

      String task = app_env.ECS_TASK_NAME ?:
                  { error "must define AWS ECS Task Name in ecs library." }()
      
      String cluster = app_env.ECS_CLUSTER_NAME ?:
                     { error "must define AWS ECS Cluster Name in ecs library." }() 

      String service = app_env.ECS_SERVICE_NAME ?: 
                     { error "must define AWS ECS Service Name to update in ecs library." }() 

      inside_sdp_image("aws"){
         withAWS(region: region){
            assumeRole() 
            String image;
            //If deploying to Dev (1st env), expect the image to have been built in the current pipeline run
            if(app_env.equals(Dev)){
               def imageInfo = get_images_to_build().first()
               image = "${imageInfo.registry}/${imageInfo.repo}:${imageInfo.tag}"
               //If deploying to Test, use the image currently in Dev
            } else if (app_env.equals(Test)){
               def rawImage = sh(script: "aws ecs describe-task-definition --task-definition ${Dev.ECS_TASK_NAME} --region '${region}' | jq '.taskDefinition | .containerDefinitions[0].image'", returnStdout: true).trim()
               image = rawImage[1..rawImage.size()-2]
               //If deploying to Prod, use the image currently in Test
            } else if (app_env.equals(Prod)){
               def rawImage = sh(script: "aws ecs describe-task-definition --task-definition ${Test.ECS_TASK_NAME} --region '${region}' | jq '.taskDefinition | .containerDefinitions[0].image'", returnStdout: true).trim()
               image = rawImage[1..rawImage.size()-2]
            }
            String newRevision = this.createNewTaskRevision(region, task, cluster, service, image)
            sh "aws ecs update-service --region ${region} --cluster ${cluster} --service ${service} --task-definition ${task}:${newRevision}"
         }
      }
   }
}

String createNewTaskRevision(String region, String task, String cluster, String service, String image){
   try{
      String output = sh(script: """
      TASK_DEFINITION=\$(aws ecs describe-task-definition --task-definition "${task}" --region "${region}")
      NEW_TASK_DEFINTIION=\$(echo \$TASK_DEFINITION | jq --arg IMAGE "${image}" '.taskDefinition | .containerDefinitions[0].image = \$IMAGE | del(.taskDefinitionArn) | del(.revision) | del(.status) | del(.requiresAttributes) | del(.compatibilities)')
      NEW_TASK_INFO=\$(aws ecs register-task-definition --region "${region}" --cli-input-json "\$NEW_TASK_DEFINTIION")
      NEW_REVISION=\$(echo \$NEW_TASK_INFO | jq '.taskDefinition.revision')
      echo NEW_REVISION=\$NEW_REVISION
      """, returnStdout: true).trim()

      def newRevision = output.readLines().find{
         it.startsWith("NEW_REVISION=")
      }.split("=").last()

      return newRevision
   }catch(any){
      println "problem creating new task revision for ${task} in ecs library"
      throw any
   }
}