var exec = require('cordova/exec');

module.exports = {

    init: function(config, success, fail) {

        exec(
            success,
            fail,
            'DatadogRum',
            'init',
            [config]
        );
    }

};