cd2mp3
======

Converts/extracts lossless audio files into lossy ones using ffmpeg.

```
usage: cd2mp3 [options] directory...

Options:
 -d <DESTINATION>    target dir to save converted files to (default: $HOME/music)
                     $ means source directory
 -h                  this help
 -l <LIST>           tracks list/range to extract from a CD audio (i.e. 1,2,9-15)
 -p <N>              preserve N last elements from source pathname
                     (default: 2, file name and parent directory)
 -q <QUALITY>        set ffmpeg compression quality (default: 6, referred to MP3)
 -t <TYPE>           lossy format type among MP3, OGG, OGA, M4A (default: MP3)
 -v                  show version number
```

It searches all directory trees starting from specified directories for files with the following suffixes: AIF, ALAC, APE, FLAC, M4A, WAV, WV.

If a .CUE with the same base name is found, it assumes an audio CD and extracts its tracks.

ffmpeg MUST be present in the system path!
