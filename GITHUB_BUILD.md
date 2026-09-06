# Building the APK on GitHub — no computer setup needed

GitHub gives every account free build machines. You upload this project once,
GitHub compiles it, and you download the finished `.apk` straight onto the
phone. A build takes about 3–5 minutes.

You can do all of this from a browser — phone or laptop, either works.

---

## 1. Make a GitHub account

Go to **github.com** and sign up (free). Verify the email it sends you.

## 2. Create an empty repository

1. Click the **+** at the top right ▸ **New repository**.
2. **Repository name:** `bag-weight-calculator`
3. Leave it **Private** if you'd rather nobody else sees it — the build works either way.
4. Do **not** tick "Add a README file".
5. Click **Create repository**.

## 3. Upload the project

On the new empty repository page, click **uploading an existing file**
(the link in "…or upload an existing file").

1. On your computer, unzip `BagWeightCalculator-android-project.zip`.
2. Open the unzipped `BagWeightCalculator` folder, select **everything inside it**
   (`app`, `gradle`, `build.gradle`, `gradlew`, `settings.gradle`, …) and drag it
   all onto the GitHub upload page.
   *Upload the contents of the folder, not the folder itself.*
3. Scroll down, click **Commit changes**.

> **If the `.github` folder didn't upload** (some browsers skip folders whose name
> starts with a dot), add it by hand — see step 4. Otherwise skip to step 5.

## 4. Only if the `.github` folder is missing

1. On the repository page: **Add file ▸ Create new file**.
2. In the file-name box type exactly:
   `.github/workflows/build-apk.yml`
   (typing the slashes creates the folders automatically)
3. Paste the contents of `.github/workflows/build-apk.yml` from the project zip
   into the big text box.
4. Click **Commit changes**.

## 5. Watch it build

Open the **Actions** tab. A run called **Build APK** starts on its own.
Click it, and you'll see the steps tick off green. If it's still yellow, it's working.

If nothing started: Actions tab ▸ **Build APK** in the left sidebar ▸
**Run workflow** ▸ **Run workflow**.

## 6. Get the APK onto the phone

When the run finishes (green tick), go to the **Releases** section on the
repository's front page — there'll be a release like **v1.0.1** with
`BagWeightCalculator-v1.apk` attached.

**On the phone:** open that release page in Chrome and tap the `.apk` file.
Android will ask to allow installs from Chrome — allow it, then tap **Install**.

That's it. The app appears in your app drawer.

*(There's a second copy under the run page's "Artifacts" section, but that one
downloads as a .zip you'd have to unpack — the Releases link is the easy one.)*

---

## Later: changing the app

Edit any file straight in GitHub (open it, click the pencil, **Commit changes**)
and a new build starts automatically with a new version number. Download the new
APK from Releases and install it over the old one — your saved loads stay put.

## If a build fails

Open the red run, click the failed step, and read the last few lines. The usual
causes:

* **`Permission denied: ./gradlew`** — the workflow already runs `chmod +x`, so
  this means the upload missed `gradlew`. Re-upload that file.
* **`SDK location not found`** — `local.properties` got uploaded by mistake.
  Delete it from the repository; the build machine sets its own SDK path.
* **AGP version errors** — change the version in `build.gradle`
  (`id 'com.android.application' version '8.10.1'`) to the one the message asks for.

Paste the error to me and I'll sort it out.
