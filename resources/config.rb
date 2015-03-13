set :source, 'src'

files.watch :source, path: File.join(root, 'vendor_src'), priority: 100

set :css_dir, 'style'

set :images_dir, 'images'

set :layouts_dir, 'structure/layouts'

app.compass_config do |config|
  require 'breakpoint'
  config.output_style = :compact
end

activate :directory_indexes

activate :syntax, line_numbers: true

set :markdown_engine, :kramdown

set :markdown, :fenced_code_blocks => true, :smartypants => true

Dir["src/structure/components/**/_*.haml"].each do |raw_name|
  partial_name = raw_name.sub(/src\//,"").sub(/_(.*)\.haml/,'\1')
  component_name = raw_name.gsub(/src\/(.*)\/_(.*).haml/,'/\1/\2.html')
  proxy component_name, "/component_template.html", :locals => { :partial_name => partial_name }, :ignore => true
end

configure :build do
  activate :minify_css
  activate :minify_javascript
  activate :gzip
  ignore 'structure/layouts*'
end
