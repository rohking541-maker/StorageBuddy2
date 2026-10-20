# Storage Buddy (Android)

Phone ki storage dikhata hai aur batata hai kya fultu hai.

- **Chamakta + tagdi animation** = zaroori cheez (rakho)
- **Dull / dhundhla** = fultu cheez (hata sakte ho)
- Dhoondhta hai: purane **screenshots**, **duplicate photos**, **bade videos (100 MB+)**
- Kuch bhi apne aap delete nahi hota. Android khud ek confirmation dialog dikhata hai.
- App mein **internet permission nahi hai**, sab kuch phone ke andar hi rehta hai.

## APK kaise banaye (sabse aasan: koi software install nahi)

1. github.com pe free account banao, phir **New repository** banao (naam: StorageBuddy).
2. Is poore folder ki files repository mein upload karo (**Add file > Upload files**).
   Dhyan rakho `.github` folder bhi upload ho (hidden folder hai).
3. Repository mein **Actions** tab kholo > **Build APK** > **Run workflow**.
4. 3-5 minute baad run ke andar neeche **Artifacts** mein `StorageBuddy-apk` milega. Download karo, zip kholo.
5. `app-debug.apk` ko phone mein bhejo, install karo (Settings mein "Unknown sources / Install unknown apps" allow karna padega).

## Dusra tareeka: Android Studio

1. Android Studio install karo (free).
2. **Open** se is folder ko kholo, Gradle sync hone do.
3. Phone USB se jodo (USB debugging on) aur **Run** dabao. Ya **Build > Build APK(s)**.

## Dhyan do

- Ye debug APK hai: khud ke phone pe chalane ke liye theek hai.
- Play Store pe daalne ke liye signed release bundle (AAB) banana padta hai aur developer account (~$25 ek baar) chahiye.
- Minimum Android 11 (API 30).
- Apps ki cache Android kisi aur app ko clear nahi karne deta, isliye wo Settings se hi hoti hai. App ek button deta hai jo seedha Storage settings kholta hai.
