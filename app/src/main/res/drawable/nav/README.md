# Bottom navigation icon overrides

Place custom vector drawables for the bottom navigation in this directory using these exact file names:

- `ic_nav_home_inactive.xml`
- `ic_nav_home_active.xml`
- `ic_nav_subscriptions_inactive.xml`
- `ic_nav_subscriptions_active.xml`
- `ic_nav_playlists_inactive.xml`
- `ic_nav_playlists_active.xml`
- `ic_nav_downloads_inactive.xml`
- `ic_nav_downloads_active.xml`

During the Gradle build, any missing custom icon is generated from the current fallback icon in
`app/src/main/res/drawable/`.
