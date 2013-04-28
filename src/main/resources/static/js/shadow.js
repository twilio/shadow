var app = angular.module('shadowApp', ['ngGrid']);


app.controller('ResultsCtrl', function($scope, $filter){

    var STATUS_DIFF = 1;
    var BODY_DIFF = 2;

    $scope.results = [];
    $scope.currentResult = '';

    $scope.filteredResults = [];

    $scope.gridOptions = {
        data: 'filteredResults',
        columnDefs: [
            {field: 'index', displayName:'No', width: "*"},
            {field: '_incoming', displayName: 'Incoming Request', width: "****"},
            {field: '_diffs', displayName: ' ', width: "***"}
        ],
        primaryKey: 'index',
        showFooter: false,
        enableSorting: false,
        multiSelect: false,
        rowTemplate: 'static/rowTemplate.html',
        showFilter: false,
        filterOptions: {
            useExternalFilter: true
        },
        afterSelectionChange: function(row) {
            $scope.selectResult(row.rowIndex, row.entity);
        }
    };
//
//    $scope.$on("rowSelected", function(event, row){
//        $scope.selectResult(row.rowIndex, row.entity);
//    });

    $scope.selectResult = function(rowIndex, entity) {
//        $scope.gridOptions.selectRow(rowIndex, true);

        $scope.followState = false;

        var result = entity;

        var bodies = _.pluck(result.results, 'body');
        var diff = JsDiff.diffChars(bodies[0], bodies[1]);

        if(result.results[1].body_diff === undefined){

            var diffs =_.map(diff, function(x){
                if(x.added === true){
                    return $("<span>").addClass("ins").text(x.value)[0].outerHTML;
                }else if(x.removed === true){
                    return $("<span>").addClass("del").text(x.value)[0].outerHTML;
                }else{
                    return $("<span>").text(x.value)[0].outerHTML;
                }
            });

            result.results[1].body_diff = diffs.join("");

            result.results[1].show_diff = diffs.length <= 8;
        }

        $scope.currentResult = result;
        $scope.prev = rowIndex - 1;
        $scope.next = rowIndex + 1;

    }

    $scope.$on('ngGridEventData', function(){
        if($scope.followState) {
            $scope.scrollBottom();
        }
    });

    $scope.scrollToSelected = function(){
        $scope.gridOptions.ngGrid.$viewport.scrollTop($('.selected.ngRow').first().position().top);
    }

    $scope.nextResult = function(){
        $scope.gridOptions.selectRow($scope.next, true);
        $scope.gridOptions.ngGrid.$viewport.scrollTop(($scope.next - 1) * $scope.gridOptions.ngGrid.config.rowHeight);
    };

    $scope.prevResult = function(){
        $scope.gridOptions.selectRow($scope.prev, true);
        $scope.gridOptions.ngGrid.$viewport.scrollTop(($scope.prev + 1) * $scope.gridOptions.ngGrid.config.rowHeight);
    };

    $scope.scrollBottom = _.debounce(_.throttle(function(){
        $scope.gridOptions.ngGrid.$viewport.scrollTop($scope.gridOptions.ngGrid.$canvas.height());
    }, 30), 100);

    $scope.resultsFilter = {
        success: {
            count: 0,
            state: true,
            clazz: 'label-success'
        },
        status_code_diff: {
            count: 0,
            state: true,
            clazz: 'label-important'
        },
        body_diff: {
            count: 0,
            state: true,
            clazz: 'label-warning'
        }
    };

    $scope.showDiffBtn = {
        true: {
            label: 'Hide Diff',
            clazz: 'btn-inverse'
        },
        false: {
            label: 'Show Diff',
            clazz: ''
        }
    };

    $scope.followBtn = {
        true: {
            label: 'Un-Follow',
            clazz: 'btn-inverse'
        },
        false: {
            label: 'Follow',
            clazz: ''
        }
    };

    $scope.followState = false;

    $scope.eventSource = new EventSource('/stream');
    $scope.eventSource.addEventListener('message', function(ev) {
        var data = JSON.parse(ev.data);
        $scope.addResult(data);
    }, false)


    $scope.count = 1;

    var throttledApply = _.throttle(function(){
        $scope.$apply();
    }, 100);

    $scope.addResult = function(result){
        status_code_diff = result.results[0].status_code != result.results[1].status_code;
        body_diff = result.results[0].body != result.results[1].body;

        result['status_code_diff'] = status_code_diff;
        result['body_diff'] = body_diff;
        result['success'] = !body_diff && !status_code_diff;

        angular.forEach(['status_code_diff', 'body_diff', 'success'], function(filter_name){
            if(result[filter_name]){
                $scope.resultsFilter[filter_name].count++;
            }
        });

        result['index'] = $scope.count++;

        $scope.results.push(result);

        if($scope.filterResult(result)) {
            $scope.filteredResults.push(result);
        }

        throttledApply();
    };

    $scope.toggleFilter = function(filter_name){
        var filter = $scope.resultsFilter[filter_name];
        filter.state = !filter.state;

        $scope.applyFilter();
        _.debounce($scope.scrollToSelected, 100)();
    };

    $scope.applyFilter = function() {
        $scope.filteredResults = $filter('filter')($scope.results, $scope.filterResult);
    }

    $scope.filterResult = function(result) {
        var show = true;
        angular.forEach(['status_code_diff', 'body_diff', 'success'], function(filter){
            if (!$scope.resultsFilter[filter].state && result[filter]){
                show = false;
            }
        });

        return show;
    }

    $scope.filterClass = function(filter_name){
        var filter = $scope.resultsFilter[filter_name];
        return (filter.state) ? filter.clazz : '';
    };

    $scope.deleteNonError = function(){
        $scope.results = _.filter($scope.results, function(result){
            return !result.success;
        });
        angular.forEach(['status_code_diff', 'body_diff', 'success'], function(filter_name){
            $scope.resultsFilter[filter_name].count = _.chain($scope.results).filter(function(result){
                return result[filter_name];
            }).size().value();
        });
        $scope.applyFilter();
    };

    $scope.emptyResults = function(){
        $scope.results=[];
        angular.forEach(['status_code_diff', 'body_diff', 'success'], function(filter_name){
            $scope.resultsFilter[filter_name].count = 0;
        });
        $scope.applyFilter();
    };

    $scope.$watch('currentResult', function(result){
        if(result !== ''){
            $scope.orig = result.results[0];
            $scope.shadowed = result.results[1];
        }
    });

    $scope.removeResult = function(index){
        var result = $scope.results[index];
        angular.forEach(['status_code_diff', 'body_diff', 'success'], function(filter_name){
            if(result[filter_name]){
                $scope.resultsFilter[filter_name].count--;
            }
        });
        $scope.results.splice(index, 1);
        if(index === $scope.results.length){
            index-=1;
        }
//        $scope.setCurrentResult($scope.results[index]);
        $scope.gridOptions.selectRow(index, true);
        $scope.applyFilter();
    };

    /*$scope.setCurrentResult = function(result, index, results){
        $scope.followState = false;

        var table = angular.element("#scrollableTable");
        var row = angular.element("#scrollableTable tbody tr").eq(index+1);

        table.scrollTop(row.position().top);

        var bodies = _.pluck(result.results, 'body');
        var diff = JsDiff.diffChars(bodies[0], bodies[1]);

        $scope.prev = {
            result: results[Math.max(0, index - 1)],
            index: Math.max(0, index - 1),
            results: results
        };

        $scope.next = {
            result: results[Math.min(index + 1, results.length)],
            index: Math.min(index + 1, results.length),
            results: results
        };

        if(result.results[1].body_diff === undefined){

            var diffs =_.map(diff, function(x){
                if(x.added === true){
                    return $("<span>").addClass("ins").text(x.value)[0].outerHTML;
                }else if(x.removed === true){
                    return $("<span>").addClass("del").text(x.value)[0].outerHTML;
                }else{
                    return $("<span>").text(x.value)[0].outerHTML;
                }
            });

            result.results[1].body_diff = diffs.join("");

            if(diffs.length > 8){
                result.results[1].show_diff = false;
            }else{
                result.results[1].show_diff = true;
            }
        }
        $scope.currentResult = result;
    };*/
});
