WEB_SOCKET_SWF_LOCATION = "/WebSocketMain.swf";
WEB_SOCKET_DEBUG = true;

function ResultsCtrl($scope) {
    $scope.results = [];
    $scope.currentResult = '';

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

    $scope.followInterval = setInterval(function(){
        if($scope.followState){
            var bounding = angular.element('#scrollableTable');
            var table = angular.element('#scrollableTable > table');
            bounding.scrollTop(table.height());
        }
    }, 100);

    $scope.followState = false;

    $scope.socket = io.connect();
    $scope.socket.on('results', function(result){
		$scope.addResult(result);
	});

    $scope.count = 0;

    $scope.addResult = function(result){
        status_code_diff = _.chain(result.results).pluck('status_code').uniq().size().value() > 1;
        body_diff = _.chain(result.results).pluck('body').uniq().size().value() > 1;
        result['status_code_diff'] = status_code_diff;
        result['body_diff'] = body_diff;
        result['success'] = !body_diff && !status_code_diff;

        angular.forEach(['status_code_diff', 'body_diff', 'success'], function(filter_name){
            if(result[filter_name]){
                $scope.resultsFilter[filter_name].count++;
            }
        });

        result['index'] = $scope.count++;
        
		$scope.$apply(function(){
            $scope.results.push(result);
        });
    };

    $scope.toggleFilter = function(filter_name){
        var filter = $scope.resultsFilter[filter_name];
        filter.state = !filter.state;
    };

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
    };

    $scope.filterResults = function(result){
        return _.chain($scope.resultsFilter).keys().map(function(name){
            return (result[name] === true) && ($scope.resultsFilter[name].state == result[name]);
        }).any().value();
    };

    $scope.emptyResults = function(){
        $scope.results=[];
        angular.forEach(['status_code_diff', 'body_diff', 'success'], function(filter_name){
            $scope.resultsFilter[filter_name].count = 0;
        });
    };

    testAdd = function(){
        var num = Math.random();
        if(num < 0.3){
            $scope.addResult({"request":{"url":"/abc2.html","headers":{"Host":"localhost:8081","Accept":"*/*","User-Agent":"curl/7.24.0 (x86_64-apple-darwin12.0) libcurl/7.24.0 OpenSSL/0.9.8r zlib/1.2.5"},"post":{},"method":"GET","get":{}},"results":[{"body":"This is not the url you are looking for","headers":{"date":"Wed, 08 Aug 2012 22:21:33 GMT","content-length":"39","content-type":"text/html; charset=utf-8"},"elapsed_time":0.014795064926147461,"type":"http_response","status_code":404},{"body":"This is not the url you are looki2ng for","headers":{"date":"Wed, 08 Aug 2012 22:21:33 GMT","content-length":"39","content-type":"text/html; charset=utf-8"},"elapsed_time":0.01584911346435547,"type":"http_response","status_code":405}]});
        }else if(num < 0.7){
            $scope.addResult({"request":{"url":"/abc2.html","headers":{"Host":"localhost:8081","Accept":"*/*","User-Agent":"curl/7.24.0 (x86_64-apple-darwin12.0) libcurl/7.24.0 OpenSSL/0.9.8r zlib/1.2.5"},"post":{},"method":"GET","get":{}},"results":[{"body":"This is not the url you are looking for","headers":{"date":"Wed, 08 Aug 2012 22:21:33 GMT","content-length":"39","content-type":"text/html; charset=utf-8"},"elapsed_time":0.014795064926147461,"type":"http_response","status_code":404},{"body":"This is not the url you are looki2ng for","headers":{"date":"Wed, 08 Aug 2012 22:21:33 GMT","content-length":"39","content-type":"text/html; charset=utf-8"},"elapsed_time":0.01584911346435547,"type":"http_response","status_code":404}]});
        }else{
            $scope.addResult({"request":{"url":"/abc2.html","headers":{"Host":"localhost:8081","Accept":"*/*","User-Agent":"curl/7.24.0 (x86_64-apple-darwin12.0) libcurl/7.24.0 OpenSSL/0.9.8r zlib/1.2.5"},"post":{},"method":"GET","get":{}},"results":[{"body":"This is not the url you are looking for","headers":{"date":"Wed, 08 Aug 2012 22:21:33 GMT","content-length":"39","content-type":"text/html; charset=utf-8"},"elapsed_time":0.014795064926147461,"type":"http_response","status_code":404},{"body":"This is not the url you are looking for","headers":{"date":"Wed, 08 Aug 2012 22:21:33 GMT","content-length":"39","content-type":"text/html; charset=utf-8"},"elapsed_time":0.01584911346435547,"type":"http_response","status_code":404}]});
        }
    };

    testAddMany = function(){
        for(var i=0; i < 100; i++){
            testAdd();
        }
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
        $scope.setCurrentResult($scope.results[index]);
    };

    $scope.setCurrentResult = function(result){
        $scope.currentResult = result;
    };
    
}
