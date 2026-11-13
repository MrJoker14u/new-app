# TV Launcher (Mi TV 4A / Android TV 9)

Lightweight, native Java launcher — koi external library nahi, koi permission nahi.
Date + clock + 1-row dock (DOWN / MENU se 2-row), wallpaper, minimal settings.

## Remote keys
| Key | Kaam |
|---|---|
| LEFT / RIGHT | Agla / pichla app |
| DOWN (compact me) | 2-row mode on |
| UP (top row se) / BACK / MENU | 1-row mode wapas |
| MENU | Toggle (kahin se bhi) |
| OK | App open |
| OK **hold** karo | App ko sabse pehle position par pin karo |
| Last tile (gear) | Launcher settings |

## APK kaise banaye (2 aasaan tareeke)

### Tareeka A — GitHub Actions (Android Studio ki zaroorat nahi)
1. github.com par free account -> New repository (private bhi chalega).
2. Is poore folder ko upload karo (`.github` folder zaroor ho).
3. Repo me **Actions** tab -> "Build APK" -> **Run workflow**.
4. 3-5 min baad run khulega -> neeche **Artifacts** -> `TVLauncher-apk` download karo -> zip me `app-release.apk`.

### Tareeka B — Android Studio (Windows)
1. Android Studio install karo, **Open** -> yeh folder select karo, Gradle sync hone do (SDK 34 auto download hoga).
2. Menu: Build -> Build APK(s). Release APK: `app/build/outputs/apk/release/app-release.apk`
   (Release variant chahiye: Build -> Select Build Variant -> release).

## TV par install (ADB, Windows)
1. TV: Settings -> Device Preferences -> About -> **Build** par 7 baar OK -> Developer options -> **USB debugging** + **Network debugging** ON.
2. PC aur TV ek hi WiFi par. TV ka IP: Settings -> Network.
3. PC par [Platform Tools](https://developer.android.com/tools/releases/platform-tools) unzip karo, us folder me CMD kholo:
```
adb connect TV_IP:5555
adb install -r app-release.apk
```
(TV par "Allow USB debugging" popup aaye to Always allow.)

## Safe testing (default NAHI banta)
```
adb shell am start -n com.dk.tvlauncher/.LauncherActivity
```
Ya TV ki app list me "TV Launcher" kholo. Original Xiaomi launcher bilkul waise hi rahega.
Home dabane par agar chooser aaye to **Just once** chuno.

## Default Home banana (test ke baad)
Pehle original launcher ka naam note karo:
```
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```
Phir apna set karo:
```
adb shell cmd package set-home-activity com.dk.tvlauncher/.LauncherActivity
```
Ya Home dabao -> chooser me TV Launcher -> **Always**.

## Wapas original par
```
adb uninstall com.dk.tvlauncher
```
(Sabse safe.) Ya note kiya hua original component:
`adb shell cmd package set-home-activity <original package/activity>`

## Wallpaper
Settings -> Change wallpaper. Agar file picker na khule to:
```
adb push wallpaper.jpg /sdcard/Android/data/com.dk.tvlauncher/files/wallpaper_import.jpg
```
phir Settings -> Change wallpaper par OK. (Image auto 1920 wide tak chhoti hoti hai, RAM bachane ke liye.)

## Performance design
- Programmatic views, koi RecyclerView/AppCompat/Glide nahi.
- Clock: system ka `TIME_TICK` broadcast (har minute) — timer/polling nahi.
- Icons: ek baar background thread me 64dp rounded bitmap, cache me; app install/remove par hi refresh.
- Expand/collapse: wahi views rows me move hote hain, sirf fade+slide (140 ms). Wallpaper dobara load nahi hota.
- Wallpaper RGB_565, screen size par decode.
- Focus: sirf scale 1.12 + ring + label fade (120 ms).
