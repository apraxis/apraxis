require 'rubygems'

if require 'bundler'
  return "Already installed"
else
  Gem.install 'bundler'
  return "Installed bundler"
end

