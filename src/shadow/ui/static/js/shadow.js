var WEB_SOCKET_SWF_LOCATION = "/WebSocketMain.swf";
var WEB_SOCKET_DEBUG = false;

function ResultsCtrl($scope) {
    // holds everything that comes in
    $scope.results = [];

    // holds the current selected result
    $scope.currentResult = '';

    // states of result filters
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

    // state of show/hide diff button
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

    // state of follow button
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

    // keep it scrolling based on the state of the follow button
    $scope.followInterval = setInterval(function(){
        if($scope.followState){
            var bounding = angular.element('#scrollableTable');
            var table = angular.element('#scrollableTable > table');
            bounding.scrollTop(table.height());
        }
    }, 100);

    // default is not to follow
    $scope.followState = false;

    // initiate the web socket connection
    $scope.socket = io.connect();

    // subscribe to results pubbed
    $scope.socket.on('results', function(result){
        $scope.addResult(result);
    });

    // counts the number of results
    $scope.count = 0;

    // calls whenever a new result comes in
    $scope.addResult = function(result){

        // check if the status codes are different
        var status_code_diff = _.chain(result.results).pluck('status_code').uniq().size().value() > 1;
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

        // create the states once
        result['status_code_diff'] = status_code_diff;
        result['body_diff'] = body_diff;
        result['success'] = !body_diff && !status_code_diff;

        // increment the counts for each of the state counters
        angular.forEach(['status_code_diff', 'body_diff', 'success'], function(filter_name){
            if(result[filter_name]){
                $scope.resultsFilter[filter_name].count++;
            }
        });

        // increment the result index
        result['index'] = $scope.count++;

        // add the result to the results list and effect the change on the UI
        $scope.$apply(function(){
            $scope.results.push(result);
        });
    };

    $scope.toggleFilter = function(filter_name){
        var filter = $scope.resultsFilter[filter_name];
        filter.state = !filter.state;
    };

    // returns the filter css class based on state
    $scope.filterClass = function(filter_name){
        var filter = $scope.resultsFilter[filter_name];
        return (filter.state) ? filter.clazz : '';
    };

    // deletes successful requests (requests without differences)
    $scope.deleteNonError = function(){

        // filters the successes out
        $scope.results = _.filter($scope.results, function(result){
            return !result.success;
        });

        // re-count all the remaining results
        angular.forEach(['status_code_diff', 'body_diff', 'success'], function(filter_name){
                $scope.resultsFilter[filter_name].count = _.chain($scope.results).filter(function(result){
                    return result[filter_name];
                }).size().value();
        });
    };

    // filters the results based on the active filters
    $scope.filterResults = function(result){
        return _.chain($scope.resultsFilter).keys().map(function(name){
            return (result[name] === true) && ($scope.resultsFilter[name].state == result[name]);
        }).any().value();
    };

    // clears the results and re-calculates the state counts on filters
    $scope.emptyResults = function(){
        $scope.results=[];
        angular.forEach(['status_code_diff', 'body_diff', 'success'], function(filter_name){
            $scope.resultsFilter[filter_name].count = 0;
        });
    };

    // creates a watch on current result and updates the orig and shadowed responses
    $scope.$watch('currentResult', function(result){
        if(result !== ''){
            $scope.orig = result.results[0];
            $scope.shadowed = result.results[1];
        }
    });

    // deletes a result from the result set
    $scope.removeResult = function(index){
        var result = $scope.results[index];

        // fix the counts
        angular.forEach(['status_code_diff', 'body_diff', 'success'], function(filter_name){
            if(result[filter_name]){
                $scope.resultsFilter[filter_name].count--;
            }
        });

        // remove the result
        $scope.results.splice(index, 1);

        // move the current result to the next result if we are at the end go to prev
        if(index === $scope.results.length){
            index-=1;
        }
        $scope.setCurrentResult($scope.results[index]);
    };

    // sets the current result and show it on the UI
    $scope.setCurrentResult = function(result, index, results){

        // once the user selects a result stop following
        $scope.followState = false;

        var table = angular.element("#scrollableTable");
        var row = angular.element("#scrollableTable tbody tr").eq(index+1);

        // scroll the list so that the current result is at the top of the list
        table.scrollTop(row.position().top);

        // set the previous
        $scope.prev = {
            result: results[Math.max(0, index - 1)],
            index: Math.max(0, index - 1),
            results: results
        };

        // set the next
        $scope.next = {
            result: results[Math.min(index + 1, results.length)],
            index: Math.min(index + 1, results.length),
            results: results
        };

        // construct the diff elements
        if(result.results[1].body_diff === undefined){
            // only do this once and cache it

            // extract the body content and diff them
            var bodies = _.pluck(result.results, 'body');
            var diff = JsDiff.diffChars(bodies[0], bodies[1]);

            // for each of the diffs generate the highlights
            var diffs =_.map(diff, function(x){
                if(x.added === true){
                    return $("<span>").addClass("ins").text(x.value)[0].outerHTML;
                }else if(x.removed === true){
                    return $("<span>").addClass("del").text(x.value)[0].outerHTML;
                }else{
                    return $("<span>").text(x.value)[0].outerHTML;
                }
            });

            // put eveything back together
            result.results[1].body_diff = diffs.join("");

            // only show the highlights by default if there not to many differences
            if(diffs.length > 8){
                result.results[1].show_diff = false;
            }else{
                result.results[1].show_diff = true;
            }
        }

        // set the current result and display it
        $scope.currentResult = result;
    };

}
