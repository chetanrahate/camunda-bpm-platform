{
  "type" : "object",
  "properties" : {

    <@lib.property
        name = "resultType"
        type = "string"
        desc = "Indicates if the message was correlated to a message start event or an 
                intermediate message catching event. In the first case, the resultType is 
                `ProcessDefinition` and otherwise `Execution`."/>

    <@lib.property
        name = "processInstance"
        type = "object"
        dto = "ProcessInstanceDto"
        desc = "This property only has a value if the resultType is set to `ProcessDefinition`. 
                The processInstance with the properties as described in the
                [get single instance](${docsUrl}/reference/rest/process-instance/get/) method."/>

    <@lib.property
        name = "execution"
        type = "object"
        dto = "ExecutionDto"
        desc = "This property only has a value if the resultType is set to `Execution`.
                The execution with the properties as described in the
                [get single execution](${docsUrl}/reference/rest/execution/get/) method."/>
  }
}
