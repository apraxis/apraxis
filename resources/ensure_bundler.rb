require 'rubygems/gem_runner'

begin
  newest_bundler = Gem.latest_version_for("bundler").version
  require 'bundler'
  if newest_bundler != Bundler::VERSION
    raise Error.new("Bundler version out of date.")
  end
rescue
  Gem::GemRunner.new.run ['install', 'bundler']
end

