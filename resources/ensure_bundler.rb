require 'rubygems'

begin
  require 'bundler'
  return "Already installed"
rescue LoadError => e
  Gem.install 'bundler'
  return "Installed bundler"
end

