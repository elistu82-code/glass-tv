package de.glasslauncher.tv.testing;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded, self-targeted release probe. Never stops apps, changes settings or suppresses HOME services. */
public class ReleaseProbe extends Instrumentation {
    private UiAutomation ui;
    private Bundle args;
    private volatile long pendingInput;
    private final List<Long> latencies=Collections.synchronizedList(new ArrayList<>());
    private String shell(String command) throws Exception {
        try (ParcelFileDescriptor descriptor=ui.executeShellCommand(command);
             InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(descriptor);
             ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192];int count;
            while((count=input.read(buffer))!=-1) output.write(buffer,0,count);
            return output.toString("UTF-8");
        }
    }
    private void save(String name,String value) throws Exception {
        try(FileOutputStream output=new FileOutputStream(new File(getContext().getFilesDir(),name))) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }
    private long key(int code) {
        long at=SystemClock.uptimeMillis();pendingInput=at;
        if(!ui.injectInputEvent(new KeyEvent(at,at,KeyEvent.ACTION_DOWN,code,0),true) ||
           !ui.injectInputEvent(new KeyEvent(at,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,code,0),true))
            throw new AssertionError("Input injection failed");
        return at;
    }
    private boolean glassDock(AccessibilityNodeInfo node) {
        if(node==null) return false;
        if("de.glasslauncher.tv".contentEquals(node.getPackageName()==null ? "" : node.getPackageName())) {
            android.graphics.Rect bounds=new android.graphics.Rect();node.getBoundsInScreen(bounds);
            if(node.isFocusable() && bounds.top>=600 && bounds.width()>180 && bounds.height()>100) return true;
        }
        for(int index=0;index<node.getChildCount();index++) {
            AccessibilityNodeInfo child=node.getChild(index);
            if(child!=null) { boolean found=glassDock(child);child.recycle();if(found) return true; }
        }
        return false;
    }
    private long awaitGlass() throws Exception {
        long deadline=SystemClock.uptimeMillis()+15000;
        while(SystemClock.uptimeMillis()<deadline) {
            AccessibilityNodeInfo root=ui.getRootInActiveWindow();
            if(root!=null) {
                boolean found=glassDock(root);root.recycle();
                if(found) return SystemClock.uptimeMillis();
            }
            Thread.sleep(25); // Bounded readiness polling, not an assumed startup delay.
        }
        throw new AssertionError("Glass dock did not become accessible");
    }
    private boolean containsLabel(AccessibilityNodeInfo node,String label,boolean requireFocus) {
        if(node==null) return false;
        CharSequence description=node.getContentDescription();
        if(description!=null && description.toString().contains(label) && (!requireFocus || focusedWithin(node))) return true;
        for(int index=0;index<node.getChildCount();index++) {
            AccessibilityNodeInfo child=node.getChild(index);
            if(child!=null) { boolean found=containsLabel(child,label,requireFocus);child.recycle();if(found) return true; }
        }
        return false;
    }
    private boolean focusedWithin(AccessibilityNodeInfo node) {
        if(node.isFocused()) return true;
        for(int index=0;index<node.getChildCount();index++) {
            AccessibilityNodeInfo child=node.getChild(index);
            if(child!=null) { boolean found=focusedWithin(child);child.recycle();if(found) return true; }
        }
        return false;
    }
    private boolean label(String name,boolean focused) {
        AccessibilityNodeInfo root=ui.getRootInActiveWindow();
        if(root==null) return false;
        boolean result=containsLabel(root,name,focused);root.recycle();return result;
    }
    private int spotifyState() throws Exception {
        String dump=shell("dumpsys media_session");int start=dump.indexOf("package=com.spotify.tv.android");
        if(start<0) return -1;
        java.util.regex.Matcher match=java.util.regex.Pattern.compile("state=PlaybackState \\{state=(\\d+)").matcher(dump.substring(start));
        return match.find()?Integer.parseInt(match.group(1)):-1;
    }
    private void coverShortcut(JSONObject report) throws Exception {
        JSONObject cover=new JSONObject();report.put("coverShortcut",cover);
        if(!label("Wiedergabe",false) && !label("Pause",false)) {
            // Normal app open only; no playback/transfer command, after the performance sample.
            shell("am start -W -n com.spotify.tv.android/.SpotifyTVActivity");
            key(KeyEvent.KEYCODE_HOME);awaitGlass();
            long ready=SystemClock.uptimeMillis()+5000;
            while(!label("Wiedergabe",false) && !label("Pause",false) && SystemClock.uptimeMillis()<ready) Thread.sleep(25);
            cover.put("preparedByNormalSpotifyOpen",true);
        }
        if(!label("Wiedergabe",false) && !label("Pause",false)) { cover.put("available",false);return; }
        cover.put("available",true);int before=spotifyState();
        key(KeyEvent.KEYCODE_DPAD_UP);
        for(int index=0;index<3;index++) { key(KeyEvent.KEYCODE_DPAD_RIGHT);Thread.sleep(80); }
        long deadline=SystemClock.uptimeMillis()+5000;
        while(!label("Spotify öffnen",true) && SystemClock.uptimeMillis()<deadline) Thread.sleep(25);
        if(!label("Spotify öffnen",true)) throw new AssertionError("Cover not D-pad reachable on real release");
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        deadline=SystemClock.uptimeMillis()+10000;boolean opened=false;
        while(SystemClock.uptimeMillis()<deadline) {
            AccessibilityNodeInfo root=ui.getRootInActiveWindow();
            if(root!=null) { opened="com.spotify.tv.android".contentEquals(root.getPackageName()==null?"":root.getPackageName());root.recycle();if(opened) break; }
            Thread.sleep(25);
        }
        if(!opened) throw new AssertionError("Actual Spotify activity did not open");
        int after=spotifyState();
        if((before==2 || before==3) && after!=before) throw new AssertionError("Spotify play/pause state changed when opening app");
        key(KeyEvent.KEYCODE_HOME);awaitGlass();
        deadline=SystemClock.uptimeMillis()+5000;
        while(!label("Spotify öffnen",true) && SystemClock.uptimeMillis()<deadline) Thread.sleep(25);
        if(!label("Spotify öffnen",true)) throw new AssertionError("Real Home return did not restore cover focus");
        cover.put("actualAppOpened",true).put("beforePlaybackState",before).put("afterPlaybackState",after).put("homeRestoredCoverFocus",true);
        try { ui.waitForIdle(1600,5000); } catch(Exception ignored) { }
        android.graphics.Bitmap bitmap=ui.takeScreenshot();
        if(bitmap!=null) try(FileOutputStream out=new FileOutputStream(new File(getContext().getFilesDir(),"after-cover.png"))) {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);bitmap.recycle();
        }
    }
    private void external() throws Exception {
        shell("am start -W -n org.jellyfin.androidtv/.ui.startup.StartupActivity");
        long deadline=SystemClock.uptimeMillis()+8000;
        while(SystemClock.uptimeMillis()<deadline) {
            AccessibilityNodeInfo root=ui.getRootInActiveWindow();
            if(root!=null) {
                boolean found="org.jellyfin.androidtv".contentEquals(root.getPackageName()==null?"":root.getPackageName());root.recycle();
                if(found) return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("External activity not foreground");
    }
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments);args=arguments;start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            String phase="after".equals(args.getString("phase"))?"after":"before";
            ui=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            ui.setOnAccessibilityEventListener(event -> {
                long at=pendingInput;
                if(event.getEventType()==AccessibilityEvent.TYPE_VIEW_FOCUSED && at>0 &&
                   "de.glasslauncher.tv".contentEquals(event.getPackageName()==null?"":event.getPackageName())) {
                    long delta=event.getEventTime()-at;
                    if(delta>=0 && delta<1000) { latencies.add(delta);pendingInput=0; }
                }
            });
            key(KeyEvent.KEYCODE_WAKEUP);
            String initial=shell("am start -W -n de.glasslauncher.tv/.MainActivity");awaitGlass();
            JSONObject report=new JSONObject();report.put("initialStart",initial);
            JSONArray warm=new JSONArray(),home=new JSONArray();
            for(int cycle=0;cycle<3;cycle++) {
                external();long at=SystemClock.uptimeMillis();
                String start=shell("am start -W -n de.glasslauncher.tv/.MainActivity");
                long ready=awaitGlass();
                warm.put(new JSONObject().put("report",start).put("dockReadyUpperMs",ready-at));
                external();shell("dumpsys gfxinfo de.glasslauncher.tv reset");
                Log.i("GlassProbe","HOME "+phase+" "+cycle+" imminent");
                long keyAt=key(KeyEvent.KEYCODE_HOME);ready=awaitGlass();
                home.put(new JSONObject().put("keyUptimeMs",keyAt).put("dockReadyUpperMs",ready-keyAt));
                save(phase+"-home-"+cycle+"-frames.txt",shell("dumpsys gfxinfo de.glasslauncher.tv framestats"));
            }
            // Let accessibility become quiet before the navigation sample; no effects are disabled.
            try { ui.waitForIdle(400,3000); } catch(Exception ignored) { }
            key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_DOWN);
            try { ui.waitForIdle(400,3000); } catch(Exception ignored) { }
            report.put("spotifyStateAtNavigation",spotifyState());
            report.put("musicControlsVisible",label("Wiedergabe",false) || label("Pause",false));
            save(phase+"-pid.txt",shell("pidof de.glasslauncher.tv"));
            save(phase+"-cpu-start.txt",shell("cat /proc/stat"));
            shell("dumpsys gfxinfo de.glasslauncher.tv reset");
            latencies.clear();
            for(int index=0;index<24;index++) {
                long at=SystemClock.uptimeMillis();key(index<12?KeyEvent.KEYCODE_DPAD_RIGHT:KeyEvent.KEYCODE_DPAD_LEFT);
                long remaining=150-(SystemClock.uptimeMillis()-at);
                if(remaining>0) Thread.sleep(remaining); // Reproducible human-like input cadence.
            }
            pendingInput=0;
            save(phase+"-cpu-end.txt",shell("cat /proc/stat"));
            save(phase+"-navigation.txt",shell("dumpsys gfxinfo de.glasslauncher.tv framestats"));
            save(phase+"-memory.txt",shell("dumpsys meminfo"));
            save(phase+"-process-cpu.txt",shell("dumpsys cpuinfo"));
            report.put("warm",warm);report.put("home",home);
            synchronized(latencies) { report.put("inputToFocusEventMs",new JSONArray(latencies)); }
            report.put("accessibilitySuppression",false);report.put("forceStoppedApps",false);
            if("after".equals(phase)) coverShortcut(report);
            save(phase+"-release.json",report.toString(2));
            result.putString("report",phase+"-release.json");finish(0,result);
        } catch(Throwable error) {
            result.putString("error",error.toString());finish(1,result);
        }
    }
}
