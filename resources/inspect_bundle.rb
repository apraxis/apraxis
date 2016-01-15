require 'rubygems'
require 'bundler'
begin
  definition = Bundler.definition
  definition.validate_ruby!
  if definition.missing_specs.empty?
    @valid = true
  end
rescue Exception
  @valid = false
end
