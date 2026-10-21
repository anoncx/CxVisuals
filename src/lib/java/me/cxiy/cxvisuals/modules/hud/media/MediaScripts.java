package me.cxiy.cxvisuals.modules.hud.media;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MediaScripts {
  public static final String WINDOWS =
      """
            $ErrorActionPreference = 'SilentlyContinue'
            [Console]::OutputEncoding = [Text.Encoding]::UTF8
            $US = [string][char]31

            Add-Type -AssemblyName System.Runtime.WindowsRuntime
            $asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
                $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
            })[0]

            function Await($op, $type) {
                if ($null -eq $op) { return $null }
                $task = $asTaskGeneric.MakeGenericMethod($type).Invoke($null, @($op))
                if (-not $task.Wait(4000)) { return $null }
                return $task.Result
            }

            function San($s) {
                if ($null -eq $s) { return '' }
                return ($s -replace "[`r`n`t]", ' ') -replace $US, ' '
            }

            $null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]
            $null = [Windows.Storage.Streams.DataReader,Windows.Storage.Streams,ContentType=WindowsRuntime]
            $null = [Windows.Storage.Streams.IRandomAccessStreamWithContentType,Windows.Storage.Streams,ContentType=WindowsRuntime]
            $ras = [Windows.Storage.Streams.IRandomAccessStream,Windows.Storage.Streams,ContentType=WindowsRuntime]

            Remove-Item (Join-Path $PSScriptRoot 'cover-*.img') -Force

            $md5 = [System.Security.Cryptography.MD5]::Create()

            function Art($props) {
                $thumb = $props.Thumbnail
                if ($null -eq $thumb) { return '' }
                $stream = Await ($thumb.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
                if ($null -eq $stream) { return '' }
                $size = $ras.GetProperty('Size').GetValue($stream)
                if ($null -eq $size -or $size -le 0 -or $size -gt 16777216) { return '' }
                $source = $ras.GetMethod('GetInputStreamAt').Invoke($stream, @([uint64]0))
                $reader = [Windows.Storage.Streams.DataReader]::new($source)
                $loaded = Await ($reader.LoadAsync([uint32]$size)) ([uint32])
                if ($null -eq $loaded -or $loaded -le 0) { return '' }
                $bytes = New-Object byte[] ([int]$loaded)
                $reader.ReadBytes($bytes)
                $reader.Dispose()
                $name = [BitConverter]::ToString($md5.ComputeHash($bytes)).Replace('-', '').Substring(0, 16)
                $path = Join-Path $PSScriptRoot ('cover-' + $name + '.img')
                if (-not (Test-Path $path)) { [IO.File]::WriteAllBytes($path, $bytes) }
                return $path
            }

            $mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
            if ($null -eq $mgr) {
                Write-Output ('E' + $US + 'windows media session unavailable')
                exit 1
            }

            $stdin = [Console]::OpenStandardInput()
            $buf = New-Object byte[] 512
            $read = $null
            $lastId = ''
            $session = $null
            $art = ''
            $sent = ''
            $artTries = 0

            while ($true) {
                if ($null -eq $read) { $read = $stdin.ReadAsync($buf, 0, 512) }
                if ($read.IsCompleted) {
                    $count = $read.Result
                    if ($count -le 0) { break }
                    $chunk = [Text.Encoding]::UTF8.GetString($buf, 0, $count)
                    foreach ($raw in ($chunk -split "[`r`n]+")) {
                        $cmd = $raw.Trim()
                        if ($cmd -eq '' -or $null -eq $session) { continue }
                        if ($cmd -eq 'playpause') { $null = $session.TryTogglePlayPauseAsync() }
                        elseif ($cmd -eq 'next') { $null = $session.TrySkipNextAsync() }
                        elseif ($cmd -eq 'prev') { $null = $session.TrySkipPreviousAsync() }
                        elseif ($cmd.StartsWith('seek ')) {
                            [int64]$target = 0
                            if ([int64]::TryParse($cmd.Substring(5), [ref]$target)) {
                                $null = $session.TryChangePlaybackPositionAsync([int64]($target * 10000))
                            }
                        }
                    }
                    $read = $null
                }

                $session = $mgr.GetCurrentSession()
                if ($null -eq $session) {
                    if ($lastId -ne '') { $lastId = ''; Write-Output ('N' + $US) }
                    Start-Sleep -Milliseconds 400
                    continue
                }

                $props = Await ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
                if ($null -eq $props) { Start-Sleep -Milliseconds 400; continue }

                $title = San $props.Title
                $artist = San $props.Artist
                if ($artist -eq '') { $artist = San $props.AlbumArtist }
                $album = San $props.AlbumTitle
                $app = San $session.SourceAppUserModelId

                $timeline = $session.GetTimelineProperties()
                $playback = $session.GetPlaybackInfo()
                $isPlaying = ($playback.PlaybackStatus.ToString() -eq 'Playing')

                $duration = [int64]$timeline.EndTime.TotalMilliseconds
                $position = [int64]$timeline.Position.TotalMilliseconds
                if ($isPlaying) {
                    $age = ([DateTimeOffset]::Now - $timeline.LastUpdatedTime).TotalMilliseconds
                    if ($age -gt 0) { $position = $position + [int64]$age }
                }
                if ($position -lt 0) { $position = 0 }
                if ($duration -gt 0 -and $position -gt $duration) { $position = $duration }

                $id = $title + '|' + $artist + '|' + $album + '|' + $app
                $changed = ($id -ne $lastId)
                if ($changed) {
                    $lastId = $id
                    $art = ''
                    $sent = '?'
                    $artTries = 8
                }
                if ($artTries -gt 0) {
                    $artTries = $artTries - 1
                    $found = Art $props
                    if ($null -ne $found -and $found -ne '') { $art = $found }
                    $shown = $art
                    if ($shown -eq '' -and $artTries -gt 0) { $shown = '*' }
                    if ($shown -ne $sent) {
                        $sent = $shown
                        Write-Output ('T' + $US + $title + $US + $artist + $US + $album + $US + $app + $US + $duration + $US + $shown)
                    }
                }

                $state = '0'
                if ($isPlaying) { $state = '1' }
                Write-Output ('P' + $US + $position + $US + $state + $US + $duration)
                Start-Sleep -Milliseconds 350
            }
            """;

  public static final String MACOS =
      """
            US=$(printf '\\037')
            Q=$(printf '\\042')
            STATE="$1"

            read_state() {
              osascript <<'APPLESCRIPT' 2>/dev/null
            set us to (ASCII character 31)
            set res to ""
            try
                if application "Spotify" is running then
                    tell application "Spotify"
                        set st to (player state as text)
                        set pp to player position
                        set t to name of current track
                        set a to artist of current track
                        set al to album of current track
                        set d to duration of current track
                        set au to artwork url of current track
                    end tell
                    set res to "spotify" & us & st & us & (pp as text) & us & t & us & a & us & al & us & (d as text) & us & au
                else if application "Music" is running then
                    tell application "Music"
                        set st to (player state as text)
                        set pp to player position
                        set t to name of current track
                        set a to artist of current track
                        set al to album of current track
                        set d to duration of current track
                    end tell
                    set res to "music" & us & st & us & (pp as text) & us & t & us & a & us & al & us & (d as text) & us & ""
                end if
            end try
            return res
            APPLESCRIPT
            }

            poll_loop() {
              while :; do
                line=$(read_state)
                if [ -n "$line" ]; then
                  printf 'M%s%s\\n' "$US" "$line"
                  printf '%s' "$line" | cut -d"$US" -f1 > "$STATE"
                else
                  printf 'N%s\\n' "$US"
                fi
                sleep 0.4
              done
            }

            poll_loop &
            POLL_PID=$!

            while IFS= read -r cmd; do
              app=$(cat "$STATE" 2>/dev/null)
              if [ "$app" = "music" ]; then target="Music"; else target="Spotify"; fi
              case "$cmd" in
                playpause) osascript -e "tell application ${Q}${target}${Q} to playpause" >/dev/null 2>&1 ;;
                next) osascript -e "tell application ${Q}${target}${Q} to next track" >/dev/null 2>&1 ;;
                prev) osascript -e "tell application ${Q}${target}${Q} to previous track" >/dev/null 2>&1 ;;
                seek*) secs=$(( ${cmd#seek } / 1000 )); osascript -e "tell application ${Q}${target}${Q} to set player position to $secs" >/dev/null 2>&1 ;;
              esac
            done

            kill $POLL_PID 2>/dev/null
            """;

  public static final String LINUX =
      """
            US=$(printf '\\037')
            STATE="$1"
            MODE="$2"

            have() { command -v "$1" >/dev/null 2>&1; }

            pc_poll() {
              fmt="{{status}}${US}{{mpris:length}}${US}{{position}}${US}{{title}}${US}{{artist}}${US}{{album}}${US}{{mpris:artUrl}}"
              player=$(playerctl -l 2>/dev/null | head -n 1)
              [ -z "$player" ] && { printf 'N%s\\n' "$US"; return; }
              out=$(playerctl -p "$player" metadata --format "$fmt" 2>/dev/null)
              [ -z "$out" ] && { printf 'N%s\\n' "$US"; return; }
              printf '%s' "$player" > "$STATE"
              printf 'L%s%s%s%s\\n' "$US" "$player" "$US" "$out"
            }

            db_get() {
              dbus-send --session --print-reply --dest="$1" /org/mpris/MediaPlayer2 \\
                org.freedesktop.DBus.Properties.Get string:org.mpris.MediaPlayer2.Player string:"$2" 2>/dev/null
            }

            db_number() {
              db_get "$1" "$2" | awk '/int64|uint64|int32|uint32/ { v=$NF } END { print v+0 }'
            }

            db_names() {
              dbus-send --session --print-reply --dest=org.freedesktop.DBus /org/freedesktop/DBus \\
                org.freedesktop.DBus.ListNames 2>/dev/null |
                awk 'match($0, /org[.]mpris[.]MediaPlayer2[.][A-Za-z0-9_.-]+/) { print substr($0, RSTART, RLENGTH) }'
            }

            db_all() {
              dbus-send --session --print-reply --dest="$1" /org/mpris/MediaPlayer2 \\
                org.freedesktop.DBus.Properties.GetAll string:org.mpris.MediaPlayer2.Player 2>/dev/null | awk -v us="$US" '
                function unq(s,   a) { a = index(s, "\\""); if (!a) return ""; s = substr(s, a + 1); sub(/"[ \\t]*$/, "", s); return s }
                /dict entry\\(/ { st=1; next }
                st==1 { k=unq($0); if (k != "") st=2; next }
                st==2 {
                  if ($0 ~ /string "/) { m[k]=unq($0); st=0 }
                  else if ($0 ~ /int64|uint64|int32|uint32|double/) { m[k]=$NF; st=0 }
                  next
                }
                END {
                  if (NR) printf "%s%s%s%s%s%s%s%s%s%s%s%s%s", m["PlaybackStatus"], us, m["Position"], us, m["xesam:title"], us, m["xesam:artist"], us, m["xesam:album"], us, m["mpris:artUrl"], us, m["mpris:length"]+0
                }
              '
            }

            db_pick() {
              pick=""
              pickinfo=""
              for d in $(db_names); do
                got=$(db_all "$d")
                [ -z "$got" ] && continue
                if [ -z "$pick" ]; then pick="$d"; pickinfo="$got"; fi
                if [ "${got%%"$US"*}" = Playing ]; then pick="$d"; pickinfo="$got"; break; fi
              done
            }

            cur=""
            written=""
            tick=0

            db_poll() {
              tick=$((tick + 1))
              info=""
              [ -n "$cur" ] && info=$(db_all "$cur")
              if [ -z "$info" ] || { [ "${info%%"$US"*}" != Playing ] && [ "$tick" -ge 5 ]; }; then
                tick=0
                db_pick
                cur="$pick"
                info="$pickinfo"
              fi
              [ -z "$info" ] && { cur=""; written=""; printf 'N%s\\n' "$US"; return; }
              if [ "$cur" != "$written" ]; then
                printf '%s' "$cur" > "$STATE"
                written="$cur"
              fi
              status=${info%%"$US"*}
              rest=${info#*"$US"}
              pos=${rest%%"$US"*}
              rest=${rest#*"$US"}
              [ -z "$pos" ] && pos=$(db_number "$cur" Position)
              printf 'D%s%s%s%s%s%s%s%s\\n' "$US" "$cur" "$US" "$status" "$US" "$pos" "$US" "$rest"
            }

            poll_loop() {
              while :; do
                if [ "$MODE" = "playerctl" ]; then pc_poll; else db_poll; fi
                sleep 0.4
              done
            }

            poll_loop &
            POLL_PID=$!

            while IFS= read -r cmd; do
              target=$(cat "$STATE" 2>/dev/null)
              [ -z "$target" ] && continue
              if [ "$MODE" = "playerctl" ]; then
                case "$cmd" in
                  playpause) playerctl -p "$target" play-pause >/dev/null 2>&1 ;;
                  next) playerctl -p "$target" next >/dev/null 2>&1 ;;
                  prev) playerctl -p "$target" previous >/dev/null 2>&1 ;;
                  seek*) playerctl -p "$target" position $(( ${cmd#seek } / 1000 )) >/dev/null 2>&1 ;;
                esac
              else
                method=""
                case "$cmd" in
                  playpause) method=PlayPause ;;
                  next) method=Next ;;
                  prev) method=Previous ;;
                esac
                [ -n "$method" ] && dbus-send --session --dest="$target" /org/mpris/MediaPlayer2 \\
                  "org.mpris.MediaPlayer2.Player.$method" >/dev/null 2>&1
              fi
            done

            kill $POLL_PID 2>/dev/null
            """;

  private MediaScripts() {}

  public static Path materialize(Path folder, String name, String contents) throws IOException {
    Files.createDirectories(folder);
    Path file = folder.resolve(name);
    Files.writeString(file, contents, StandardCharsets.UTF_8);
    return file;
  }

  public static Path stateFile(Path folder, String name) throws IOException {
    Files.createDirectories(folder);
    Path file = folder.resolve(name);
    if (!Files.exists(file)) {
      Files.writeString(file, "", StandardCharsets.UTF_8);
    }
    return file;
  }
}
