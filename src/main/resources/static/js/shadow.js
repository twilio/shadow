var app = angular.module('shadowApp', ['ngGrid']);


app.controller('ResultsCtrl', function ($scope, $filter) {

    $scope.results = [];
    $scope.currentResult = '';

    $scope.filteredResults = [];

    $scope.gridOptions = {
        data: 'filteredResults',
        columnDefs: [
            {field: 'index', displayName: 'No', width: "*"},
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
        afterSelectionChange: function (row) {
            $scope.selectResult(row.rowIndex, row.entity);
        }
    };

    $scope.selectResult = function (rowIndex, entity) {

        $scope.followState = false;

        var bodies = _.pluck(entity.results, 'body');
        var diff = JsDiff.diffChars(bodies[0], bodies[1]);

        if (entity.results[1].body_diff === undefined) {

            var diffs = _.map(diff, function (x) {
                if (x.added === true) {
                    return $("<span>").addClass("ins").text(x.value)[0].outerHTML;
                } else if (x.removed === true) {
                    return $("<span>").addClass("del").text(x.value)[0].outerHTML;
                } else {
                    return $("<span>").text(x.value)[0].outerHTML;
                }
            });

            entity.results[1].body_diff = diffs.join("");

            entity.results[1].show_diff = diffs.length <= 8;
        }

        $scope.currentResult = entity;
        $scope.prev = rowIndex - 1;
        $scope.next = rowIndex + 1;

    };

    $scope.$on('ngGridEventData', function () {
        if ($scope.followState) {
            $scope.scrollBottom();
        }
    });

    $scope.scrollToSelected = function () {
        $scope.gridOptions.ngGrid.$viewport.scrollTop($('.selected.ngRow').first().position().top);
    };

    $scope.nextResult = function () {
        $scope.gridOptions.selectRow($scope.next, true);
        $scope.gridOptions.ngGrid.$viewport.scrollTop(($scope.next - 1) * $scope.gridOptions.ngGrid.config.rowHeight);
    };

    $scope.prevResult = function () {
        $scope.gridOptions.selectRow($scope.prev, true);
        $scope.gridOptions.ngGrid.$viewport.scrollTop(($scope.prev + 1) * $scope.gridOptions.ngGrid.config.rowHeight);
    };

    $scope.scrollBottom = _.debounce(_.throttle(function () {
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
        'true': {
            label: 'Hide Diff',
            clazz: 'btn-inverse'
        },
        'false': {
            label: 'Show Diff',
            clazz: ''
        }
    };

    $scope.followBtn = {
        'true': {
            label: 'Un-Follow',
            clazz: 'btn-inverse'
        },
        'false': {
            label: 'Follow',
            clazz: ''
        }
    };

    $scope.followState = false;

    $scope.eventSource = new EventSource('/stream');
    $scope.eventSource.addEventListener('message', function (ev) {
        var data = JSON.parse(ev.data);
        $scope.addResult(data);
    }, false);


    $scope.count = 1;

    var throttledApply = _.throttle(function () {
        $scope.$apply();
    }, 100);

    $scope.addResult = function (result) {
        var status_code_diff = result.results[0].status_code != result.results[1].status_code;
        var body_diff = false;

        var strict_check = false;

        if (_.all(result.results, function (result) {
            return (/json/i).test(result.headers['content-type']);
        })) {
            // Try comparing the parsed JSON, maybe make this optional if we
            // actually care about spacing or the order of properties in the
            // returned JSON
            try {
                _.each(result.results, function (a) {
                    var aJson = JSON.parse(a.body);

                    _.each(result.results, function (b) {
                        var bJson = JSON.parse(b.body);

                        if (!_.isEqual(aJson, bJson)) {
                            body_diff = true;
                        }
                    });
                });
            } catch (e) {
                strict_check = true;
            }
        } else {
            strict_check = true;
        }

        // strict check if the body content is different
        if (strict_check) {
            body_diff = _.chain(result.results).pluck('body').uniq().size().value() > 1;
        }

        result['status_code_diff'] = status_code_diff;
        result['body_diff'] = body_diff;
        result['success'] = !body_diff && !status_code_diff;

        angular.forEach(['status_code_diff', 'body_diff', 'success'], function (filter_name) {
            if (result[filter_name]) {
                $scope.resultsFilter[filter_name].count++;
            }
        });

        result['index'] = $scope.count++;

        $scope.results.push(result);

        if ($scope.filterResult(result)) {
            $scope.filteredResults.push(result);
        }

        throttledApply();
    };

    $scope.toggleFilter = function (filter_name) {
        var filter = $scope.resultsFilter[filter_name];
        filter.state = !filter.state;

        $scope.applyFilter();
        _.debounce($scope.scrollToSelected, 100)();
    };

    $scope.applyFilter = function () {
        $scope.filteredResults = $filter('filter')($scope.results, $scope.filterResult);
    };

    $scope.filterResult = function (result) {
        var show = false;
        angular.forEach(['status_code_diff', 'body_diff', 'success'], function (filter) {
            if ($scope.resultsFilter[filter].state && result[filter]) {
                show = true;
            }
        });

        return show;
    };

    $scope.filterClass = function (filter_name) {
        var filter = $scope.resultsFilter[filter_name];
        return (filter.state) ? filter.clazz : '';
    };

    $scope.deleteNonError = function () {
        $scope.results = _.filter($scope.results, function (result) {
            return !result.success;
        });
        angular.forEach(['status_code_diff', 'body_diff', 'success'], function (filter_name) {
            $scope.resultsFilter[filter_name].count = _.chain($scope.results).filter(function (result) {
                return result[filter_name];
            }).size().value();
        });
        $scope.applyFilter();
    };

    $scope.emptyResults = function () {
        $scope.results = [];
        angular.forEach(['status_code_diff', 'body_diff', 'success'], function (filter_name) {
            $scope.resultsFilter[filter_name].count = 0;
        });
        $scope.applyFilter();
    };

    $scope.$watch('currentResult', function (result) {
        if (result !== '') {
            $scope.orig = result.results[0];
            $scope.shadowed = result.results[1];
        }
    });

    $scope.removeResult = function (index) {
        var result = $scope.results[index];
        angular.forEach(['status_code_diff', 'body_diff', 'success'], function (filter_name) {
            if (result[filter_name]) {
                $scope.resultsFilter[filter_name].count--;
            }
        });
        $scope.results.splice(index, 1);
        if (index === $scope.results.length) {
            index -= 1;
        }
        $scope.gridOptions.selectRow(index, true);
        $scope.applyFilter();
    };

});
