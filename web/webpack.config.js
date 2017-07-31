'use strict'

const os = require('os')

const WebpackCleanupPlugin = require('webpack-cleanup-plugin')
const webpack = require('webpack')

const entryPoints = [
  'admin/Job/index',
  'admin/Plugin/index',
  'admin/User/index',
  'ApiToken/index',
  'CsvUpload/new',
  'DocumentCloudImportJob/new',
  'DocumentCloudProject/index',
  'DocumentSet/index',
  'DocumentSet/show',
  'DocumentSet/show-progress',
  'DocumentSetUser/index',
  'FileImport/new',
  'PublicDocumentSet/index',
  'SharedDocumentSet/index',
  'Welcome/show',
]

// We compile web/js/ => web/public/javascript-bundles/, staying as far away
// from sbt-web as we can.
//
// But sbt-web still generates the asset bundle, so we need it for now. That's
// why we write to web/public/* (which sbt-web builds into a jarfile) instead
// of web/target/*.
//
// TODO:
// * Less (or SCSS) compilation in Webpack -- and nix sbt-less
// * Digests: add `.md5` files and hashes to `/public/**/*.(css|js|png|jpg)`
//   in Webpack or sbt -- and nix sbt-digest
// * Change `web/public/` to `web/target/public/` and add that to
//   `unmanagedResourceDirectories` so the assets all get bundled into the same
//   jarfile as our Play code -- and nix sbt-web

/*
 * Play's AssetsFinder searches for a "versioned" .js file like this:
 * 1. Reads for [dirname]/[basename].js.md5
 * 2. Serves [dirname]/[md5]-[basename].js
 *
 * We require hashed filenames and hashed source-map filenames, so they can be
 * served with never-expire headers and browsers won't use cached versions when
 * the <script> src points to a new version.
 *
 * One solution: do everything in Webpack. Play's default
 * play.assets.digest.algorithm is "md5", and Play never actually hashes file
 * contents. That means we can pretend [chunkhash] is an md5 and write it to
 * [basename].js.md5 so Play will find the chunkhash-versioned file.
 *
 * We don't want/need this hack with chunks. Webpack is the one linking to the
 * chunks, so we want the original Webpack-generated URLs.
 *
 * Hack: Webpack's [name] seems to be atomic: we can't insert a hash between
 * dirname and filename. So we nix all folders and output a flat directory.
 */
function RedirectPlayFrameworkUsingDummyMd5Files() {}
RedirectPlayFrameworkUsingDummyMd5Files.prototype.apply = function(compiler) {
  compiler.plugin('emit', (compilation, callback) => {
    const fakeMd5Assets = []

    for (const filename in compilation.assets) {
      const pathParts = filename.split('/')
      const basename = pathParts[pathParts.length - 1]
      let m
      if (m = /^([a-f0-9]+)-(.*\.js)$/.exec(basename)) {
        const dirname = pathParts.slice(0, pathParts.length - 1).join('/')
        const hashThatIsntReallyMd5 = m[1]
        const basenameWithoutHash = m[2]
        fakeMd5Assets.push({
          filename: `${basenameWithoutHash}.md5`,
          contents: hashThatIsntReallyMd5
        })
      }
    }

    fakeMd5Assets.forEach(newAsset => {
      compilation.assets[newAsset.filename] = {
        source: () => newAsset.contents,
        size: () => newAsset.contents.length,
      }
    })

    callback()
  })
}

const entryPointsMap = {}
for (const entryPoint of entryPoints) {
  entryPointsMap[entryPoint.replace(/\//g, '-')] = `bundle/${entryPoint}`
}

const UglifyESOptions = {
  sourceMap: true, // slow, but necessary
  compress: false, // save 5s: compression is slow and barely affects file size
  mangle: true,    // costs 1.5s, but saves 25% or so in file sizes
}

module.exports = {
  context: `${__dirname}/js`,
  entry: entryPointsMap,
  devtool: 'source-map', // slow, important
  output: {
    path: `${__dirname}/public/javascript-bundles`,
    publicPath: '/assets/javascript-bundles/',
    filename: '[chunkhash]-[name].js',
    sourceMapFilename: '[file].map',
    chunkFilename: '[chunkhash].chunk.js',

    // expose a "jquery" global variable. Integration tests will use this to
    // test that loading is done.
    library: 'jQuery',
    libraryTarget: 'window',
  },
  module: {
    loaders: [
      {
        test: /\.coffee$/,
        use: [
          {
            loader: '../node/uglify-es-loader',
            options: UglifyESOptions,
          },
          {
            loader: 'coffee-loader',
            options: { sourceMap: true },
          },
        ],
      },
      {
        test: /\.js$/,
        use: [
          {
            loader: '../node/uglify-es-loader',
            options: UglifyESOptions,
          },
        ],
      },
    ],
  },
  plugins: [
    new WebpackCleanupPlugin(),
    new webpack.ProvidePlugin({
        $: "jquery",
        jQuery: "jquery",
        "window.jQuery": "jquery"
    }),
    new webpack.HashedModuleIdsPlugin(),
    new RedirectPlayFrameworkUsingDummyMd5Files(),
  ],
  resolve: {
    extensions: [ '.js', '.coffee' ],
    modules: [ '.', 'node_modules' ],
    alias: {
      // for our code:
      backbone: 'vendor/backbone',
      backform: 'vendor/backform',
      backgrid: 'vendor/backgrid',
      base64: 'vendor/base64',
      'bootstrap-alert': 'vendor/bootstrap-alert',
      'bootstrap-collapse': 'vendor/bootstrap-collapse',
      'bootstrap-dropdown': 'vendor/bootstrap-dropdown',
      'bootstrap-modal': 'vendor/bootstrap-modal',
      'bootstrap-popover': 'vendor/bootstrap-popover',
      'bootstrap-tab': 'vendor/bootstrap-tab',
      'bootstrap-toggle': 'vendor/bootstrap-toggle',
      'bootstrap-tooltip': 'vendor/bootstrap-tooltip',
      'bootstrap-transition': 'vendor/bootstrap-transition',
      html5sortable: 'vendor/html5sortable-0-6-1',
      jquery: 'vendor/jquery-2-1-0',
      'jquery.mousewheel': 'vendor/jquery.mousewheel',
      'jquery.validate': 'vendor/jquery.validate',
      'mass-upload': 'vendor/mass-upload',
      md5: 'vendor/md5',
      select2: 'vendor/select2',
      sha1: 'vendor/git-sha1',
      spectrum: 'vendor/spectrum',
      tinycolor: 'vendor/tinycolor',
      typeahead: 'vendor/typeahead.jquery',
      underscore: 'vendor/underscore',
      oboe: 'vendor/oboe-browser-2-1-3',
      MassUpload: 'vendor/mass-upload',
    }
  },
}
