require 'rubygems'
require 'bundler'

begin
  definition = Bundler.definition
  definition.validate_ruby!
  unless definition.missing_specs.empty?
    raise Error.new("Bundle missing specs.")
  end
rescue
  require 'bundler/cli'
  Bundler::CLI.start(["install","--path",vendor_path])
end
