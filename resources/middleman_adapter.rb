require 'rubygems'
require 'bundler'

Bundler.require(:default)

require 'middleman'
require 'middleman-compass'
require 'middleman-syntax'
require 'middleman-core/application'
require 'middleman-core/rack'

class MiddlemanAdapter
  def initialize
    @app = Middleman::Application.new()
    @rack = Middleman::Rack.new(@app).to_app
  end

  def raw_response(path)
    response = @rack.call({"REQUEST_METHOD" => "GET",
                            "SERVER_NAME" => "localhost",
                            "SERVER_PORT" => "8080",
                            "QUERY_STRING" => "",
                            "rack.version" => Rack::VERSION,
                            "rack.input" => StringIO.new("").tap {|io| io.set_encoding "ASCII-8BIT"},
                            "rack.errors" => StringIO.new(""),
                            "rack.multithread" => false,
                            "rack.multiprocess" => false,
                            "rack.run_once" => false,
                            "rack.url_scheme" => "http",
                            "PATH_INFO" => path,
                            "SCRIPT_NAME" => ""})
    res = []
    response[2].each do |chunk|
      res.concat chunk.bytes
    end
    {status: response[0],
      headers: response[1],
      body: res}
  end

  def response(path)
    response = raw_response(path)
    response[:body]
  end
end

MiddlemanAdapter.new()
