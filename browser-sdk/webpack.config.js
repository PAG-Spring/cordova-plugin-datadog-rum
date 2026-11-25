const path = require('path');
const webpack = require('webpack');

module.exports = {
    mode: 'production',

    entry: './index.js',

    output: {
        filename: 'datadog-rum.js',
        path: path.resolve(__dirname, 'dist'),
        clean: true
    },

    optimization: {
        splitChunks: false
    },

    plugins: [
        new webpack.optimize.LimitChunkCountPlugin({
            maxChunks: 1
        })
    ]
};

