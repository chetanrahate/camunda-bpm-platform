'use strict';

ngDefine('cockpit.directives', [ 'angular' ], function(module, angular) {

  var Directive = [ 'ProcessDefinitionResource', function (ProcessDefinitionResource) {
    return {
      restrict: 'AC',
      require: 'processDiagram',
      link: function(scope, element, attrs, processDiagram) {

        scope.$watch(attrs['processDefinitionId'], function(newValue) {
          if (newValue) {
            annotateProcessDiagram(newValue);
          }
        });

        function annotateProcessDiagram(processDefinitionId) {
          ProcessDefinitionResource
            .queryActivityStatistics({
              id : processDefinitionId,
              incidents: true
            })
            .$then(function(result) {
              processDiagram.annotateWithIncidents(result.data);
            });
        };

      }
    };
  }];

  module
    .directive('processDefinitionIncidents', Directive);

});
