![Version](https://img.shields.io/github/v/tag/michaldrabik/showly-2.0?label=version)
![Build](https://img.shields.io/github/actions/workflow/status/michaldrabik/showly-2.0/android.yml?branch=master)
[![RB Shield](https://shields.rbtlog.dev/simple/com.michaldrabik.showly2)](https://shields.rbtlog.dev/com.michaldrabik.showly2)
[![Crowdin](https://badges.crowdin.net/showly-android-app/localized.svg)](https://crowdin.com/project/showly-android-app)
[![Crowdin](https://badges.crowdin.net/showly-ios-app/localized.svg)](https://crowdin.com/project/showly-ios-app)

[![Twitter](https://img.shields.io/twitter/follow/AppShowly?style=social)](https://twitter.com/AppShowly)

# Showly available on iOS 🍎

I am happy to announce that starting Jan 2025, Showly is available on iOS!

Check AppStore link below to jump straight into action.

# Showly

<img src="https://github.com/user-attachments/assets/b31c6ce6-7257-4c90-a13b-b12603e105a9" align="left" width="180" hspace="0" vspace="80" />

Showly is a modern TV Shows and Movies tracking app.

The OSS version for Android available in this repo is completely free of all Google services.

<a href="https://play.google.com/store/apps/details?id=com.michaldrabik.showly2"><img
    alt="Get it on Google Play"
    height="80"
    src="https://github.com/user-attachments/assets/3e49d1b3-1046-4e76-ad50-dfd859c23f3a"/></a>
&nbsp;&nbsp;
<a href="https://apt.izzysoft.de/packages/com.michaldrabik.showly2"><img
    alt="Get it at IzzyOnDroid"
    height="80"
    src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png"/></a>
&nbsp;&nbsp;
<a href="https://apps.apple.com/us/app/6739016219"><img
    alt="Get it on App Store"
    height="80"
    src="https://github.com/user-attachments/assets/f43c7c55-01d8-4ac3-99dd-ca8e0f574283"/></a>

## Screenshots

<div>
   <img src="https://github.com/user-attachments/assets/84f00049-6593-4cb6-bbe3-9cff7ffd313f" width="160" alt="Screenshot 1">
  &nbsp;&nbsp;
   <img src="https://github.com/user-attachments/assets/81dcb5a1-0db0-40bd-bba4-0bb82fbc7a4e" width="160" alt="Screenshot 2">
  &nbsp;&nbsp;
   <img src="https://github.com/user-attachments/assets/a6959690-57d0-40c9-a2e3-f02216aacc71" width="160" alt="Screenshot 3">
  &nbsp;&nbsp;
  <img src="https://github.com/user-attachments/assets/255505c5-ddc4-4ae6-b130-1ef47057e9b8" width="160" alt="Screenshot 4">
</div>

## Project Setup

1. Clone repository and open project in the latest version of Android Studio.
2. Create a `keystore.properties` file and put it in the `/app` folder.
3. Add the following properties into the `keystore.properties` file (values are not important at this moment):

```
keyAlias=github
keyPassword=github
storePassword=github
```

4. Add your [Trakt.tv](https://trakt.tv/oauth/applications), [TMDB](https://developers.themoviedb.org/3/), [OMDB](http://www.omdbapi.com) API keys as
   following properties into your `local.properties` file located in the root directory of the project:

```
traktClientId="your trakt client id"
traktClientSecret="your trakt client secret"
tmdbApiKey="your tmdb api key (v4)"
omdbApiKey="your omdb api key"
```

5. Rebuild and start the app.

## Issues & Contributions

Feel free to post problems with the app as Github [Issues](https://github.com/michaldrabik/showly-2.0/issues).

Features ideas should be posted as new GIthub [Discussion](https://github.com/michaldrabik/showly-2.0/discussions).

Pull requests are welcome. Remember about leaving a comment in the relevant issue if you are working on something.

## Dev Notes

The codebase has been around for a few years now and it grew a bit rusty.
A few things surely could be addressed:

- Overall architecture should be simplified and refactored into a more strict feature-based one
- The single responsibility principle is broken and should be refactored in a few places like some of the Use Cases
- Retrofit could be replaced in favor of Ktor Client
- Jetpack Compose migration (although there is no **_real_** benefit of it currently from end-user point of view)
- Add more unit tests to complete the suite and increase coverage

## Translations

Want to help translating Showly into your native language? Spotted a mistake?<br>
Join the CrowdIn project which is used to manage translations:<br>

Android: https://crowdin.com/project/showly-android-app <br>
iOS: https://crowdin.com/project/showly-ios-app <br>

Translations status for 20 Jan 2025:

iOS:

![image](https://github.com/user-attachments/assets/e09f6a03-6c90-43fa-8f91-f17de80e1d84)

Android:

![image](https://github.com/user-attachments/assets/24736105-a9be-49ef-864e-b4f2624671f6)

## FAQ

**1. Can I watch/stream/download shows and movies with the Showly app?**

No, that is not possible. Showly is a progress tracking type of app - not a streaming service.

**2. I'm a user from India. I can't see any images and also encounter errors!**

There is a known issue with TMDB API being blocked by India gov.
For more details and a possible solution please see the thread here:
[https://www.themoviedb.org/talk/65d226e5c433ea0187b5b958#65d2dd5128d7fe017c34e9b5](https://www.themoviedb.org/talk/65d226e5c433ea0187b5b958#65d2dd5128d7fe017c34e9b5)

**3. The Show/Episode/Movie I'm looking for seems to be missing. What can I do?**

Showly uses [Trakt.tv](https://trakt.tv) as its main data source.
If something is missing please use "Import Show" / "Import Movie" option located at the bottom of Trakt.tv website.
It's also possible to contact Trakt.tv support about any related issue.

## Contact

Twitter: https://twitter.com/AppShowly

Landing Page: www.showlyapp.com

Email: showlyapp@gmail.com
