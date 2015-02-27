set :source, 'src'

set :css_dir, 'style'

set :images_dir, 'images'

set :layouts_dir, 'structure/layouts'

compass_config do |config|
  require 'breakpoint'
  config.output_style = :compact
end

activate :directory_indexes

activate :syntax, line_numbers: true

set :markdown_engine, :kramdown

set :markdown, :fenced_code_blocks => true, :smartypants => true

configure :build do
  activate :minify_css
  activate :minify_javascript
  activate :gzip
  ignore 'structure/layouts*'
end
