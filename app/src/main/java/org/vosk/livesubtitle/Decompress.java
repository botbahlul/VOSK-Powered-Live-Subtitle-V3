package org.vosk.livesubtitle;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Decompress {
    private String _zipFile;
    private String _extract_location;

    public Decompress(String zipFile, String extract_location) {
        _zipFile = zipFile;
        _extract_location = extract_location;

        _dirChecker("");
    }

    public void unzip() {
        try  {
            FileInputStream fin = new FileInputStream(_zipFile);
            ZipInputStream zin = new ZipInputStream(fin);

            byte b[] = new byte[1024];

            ZipEntry ze = null;
            while ((ze = zin.getNextEntry()) != null) {
                Log.v("Decompress", "Unzipping " + ze.getName());

                if(ze.isDirectory()) {
                    _dirChecker(ze.getName());
                } else {
                    FileOutputStream fout = new FileOutputStream(_extract_location + ze.getName());

                    BufferedInputStream in = new BufferedInputStream(zin);
                    BufferedOutputStream out = new BufferedOutputStream(fout);

                    int n;
                    while ((n = in.read(b,0,1024)) >= 0) {
                        out.write(b,0,n);
                    }

                    zin.closeEntry();
                    out.close();
                }

            }
            zin.close();
        } catch(Exception e) {
            Log.e("Decompress", "unzip", e);
        }

    }

    private void _dirChecker(String dir) {
        File f = new File(_extract_location + dir);

        if(!f.isDirectory()) {
            f.mkdirs();
        }
    }
}
