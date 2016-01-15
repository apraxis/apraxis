begin
  require 'bundler'
  newest_bundler = Gem.latest_version_for("bundler").version
  if newest_bundler != Bundler::VERSION
    raise StandardError.new("Bundler version out of date.")
  end
rescue Exception
  # Exception isn't cleared until raise exits, and GemRunner hijacks
  # executing ruby, so clear the exception then queue up the
  # GemRunner.
  $@ = nil
  $! = nil
  require 'rubygems/gem_runner'
  Gem::GemRunner.new.run ['install','bundler']
end
