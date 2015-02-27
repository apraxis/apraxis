require 'rubygems'
require 'bundler'

Bundler.require(:default)

require 'middleman-core/profiling'

require "middleman-core/load_paths"
Middleman.setup_load_paths

require 'middleman-core/cli'

Middleman::Cli::Base.start(["build"])
