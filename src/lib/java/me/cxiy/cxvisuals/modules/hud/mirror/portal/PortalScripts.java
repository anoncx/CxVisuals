package me.cxiy.cxvisuals.modules.hud.mirror.portal;

final class PortalScripts {
  static final String CAST =
      """
      import os
      import struct
      import sys
      import threading

      import gi

      gi.require_version('Gst', '1.0')
      from gi.repository import Gio, GLib, Gst

      PORTAL = 'org.freedesktop.portal.Desktop'
      PATH = '/org/freedesktop/portal/desktop'
      CAST = 'org.freedesktop.portal.ScreenCast'
      REQUEST = 'org.freedesktop.portal.Request'
      WINDOWS = 2
      PERSIST = 2

      token_file = sys.argv[1]
      fresh = len(sys.argv) > 2 and sys.argv[2] == 'fresh'
      out = sys.stdout.buffer
      lock = threading.Lock()


      def emit(kind, width, height, payload):
          with lock:
              out.write(kind + struct.pack('<III', width, height, len(payload)) + payload)
              out.flush()


      def fail(message):
          emit(b'E', 0, 0, message.encode('utf-8'))
          os._exit(1)


      def watch_input():
          sys.stdin.buffer.read()
          os._exit(0)


      threading.Thread(target=watch_input, daemon=True).start()

      Gst.init(None)
      bus = Gio.bus_get_sync(Gio.BusType.SESSION, None)
      sender = bus.get_unique_name()[1:].replace('.', '_')
      loop = GLib.MainLoop()
      counter = [0]
      session = [None]


      def saved_token():
          if fresh:
              return None
          try:
              with open(token_file, 'r') as handle:
                  value = handle.read().strip()
              return value or None
          except OSError:
              return None


      def save_token(value):
          try:
              with open(token_file, 'w') as handle:
                  handle.write(value)
          except OSError:
              pass


      def finished(connection, result, data):
          try:
              connection.call_finish(result)
          except GLib.Error as problem:
              fail(problem.message)


      def request(method, build, then):
          counter[0] += 1
          handle = 'cxv%d' % counter[0]
          path = '/org/freedesktop/portal/desktop/request/%s/%s' % (sender, handle)
          holder = {}

          def answered(connection, name, obj, interface, signal, parameters):
              bus.signal_unsubscribe(holder['id'])
              code, results = parameters.unpack()
              if code != 0:
                  fail('the window picker was closed')
              then(results)

          holder['id'] = bus.signal_subscribe(
              PORTAL, REQUEST, 'Response', path, None, Gio.DBusSignalFlags.NONE, answered)
          bus.call(
              PORTAL, PATH, CAST, method, build(handle), None,
              Gio.DBusCallFlags.NONE, -1, None, finished, None)


      def on_sample(sink):
          sample = sink.emit('pull-sample')
          if sample is None:
              return Gst.FlowReturn.OK
          shape = sample.get_caps().get_structure(0)
          buffer = sample.get_buffer()
          ok, info = buffer.map(Gst.MapFlags.READ)
          if ok:
              data = bytes(info.data)
              buffer.unmap(info)
              emit(b'F', shape.get_value('width'), shape.get_value('height'), data)
          return Gst.FlowReturn.OK


      def play(node):
          parameters = GLib.Variant('(oa{sv})', (session[0], {}))
          reply, fds = bus.call_with_unix_fd_list_sync(
              PORTAL, PATH, CAST, 'OpenPipeWireRemote', parameters,
              GLib.VariantType('(h)'), Gio.DBusCallFlags.NONE, -1, None, None)
          fd = fds.get(reply.unpack()[0])
          description = (
              'pipewiresrc fd=%d path=%d do-timestamp=true ! videoconvert ! '
              'video/x-raw,format=BGRA ! appsink name=sink emit-signals=true '
              'max-buffers=1 drop=true sync=false' % (fd, node))
          pipeline = Gst.parse_launch(description)
          pipeline.get_by_name('sink').connect('new-sample', on_sample)
          pipeline.set_state(Gst.State.PLAYING)


      def started(results):
          token = results.get('restore_token')
          if token:
              save_token(token)
          streams = results.get('streams', [])
          if not streams:
              fail('no window was shared')
          play(streams[0][0])


      def selected(results):
          request(
              'Start',
              lambda handle: GLib.Variant(
                  '(osa{sv})',
                  (session[0], '', {'handle_token': GLib.Variant('s', handle)})),
              started)


      def created(results):
          session[0] = results['session_handle']

          def build(handle):
              options = {
                  'handle_token': GLib.Variant('s', handle),
                  'types': GLib.Variant('u', WINDOWS),
                  'multiple': GLib.Variant('b', False),
                  'persist_mode': GLib.Variant('u', PERSIST),
              }
              token = saved_token()
              if token:
                  options['restore_token'] = GLib.Variant('s', token)
              return GLib.Variant('(oa{sv})', (session[0], options))

          request('SelectSources', build, selected)


      try:
          request(
              'CreateSession',
              lambda handle: GLib.Variant(
                  '(a{sv})',
                  ({
                      'handle_token': GLib.Variant('s', handle),
                      'session_handle_token': GLib.Variant('s', 'cxvisuals'),
                  },)),
              created)
          loop.run()
      except Exception as problem:
          fail(str(problem))
      """;

  private PortalScripts() {}
}
