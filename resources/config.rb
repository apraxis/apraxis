def expand_symlink(relative_path)
  File.expand_path(File.readlink(File.expand_path(relative_path, app.root)), app.root)
end

files.watch :source, path: expand_symlink('source'), priority: 100
files.watch :source, path: File.expand_path('vendor_src', app.root), priority: 100

set :css_dir, 'style'

set :images_dir, 'images'

set :layouts_dir, 'structure/layouts'

activate :syntax, line_numbers: true

compass_config do |config|
  require 'breakpoint'
  config.output_style = :compact
end

set :markdown_engine, :kramdown

set :markdown, :fenced_code_blocks => true, :smartypants => true

activate :directory_indexes

live {
  Dir["source/structure/components/**/_*.haml"]
}.each do |raw_name|
  partial_name = raw_name.sub(/source\//,"").sub(/_(.*)\.haml/,'\1')
  component_name = raw_name.gsub(/source\/(.*)\/_(.*).haml/,'/\1/\2/index.html')
  proxy component_name, "/component_template.html", :locals => { :partial_name => partial_name }, :ignore => true
end

configure :build do
  activate :minify_css
  ignore 'structure/layouts*'
  ignore "/component_template.html"
end
