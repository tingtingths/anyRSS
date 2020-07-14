# RSSTHIS

Web APIs to turns html into rss.

Endpoints
- `/xeva`
    - `src` - URI or base64 encoded html
    - `xpath` - xpath to evaluate
- `/rssthis`
    - `src` - URI or base64 encoded html
    - `title_xpath` - xpath to extract item title
    - `link_xpath` - xpath to extract item link
    - `desc_xpath` - xpath to extract item description
- `/rarbg`
    - `search_string` - string to search with
    - `search_imdb` - IMDB title id
    - `limit` - search result records limit
    - `regex` - regex to filter the search result

Examples
- `/xeva` Evaluate xpath with html
    - `src`
        - `https://www.archlinux.org/packages/?sort=&repo=Core&q=linux-&maintainer=&flagged=`  
    - `xpath`
        - `//*[@id="pkglist-results-form"]/table/tbody/tr/td[3]/a`
    ```json
    {
        "src" : "https://www.archlinux.org/packages/?sort=&repo=Core&q=linux-&maintainer=&flagged=",
        "result" : {
            "//*[@id=\"pkglist-results-form\"]/table/tbody/tr/td[3]/a" : [
                "archlinux-keyring",
                "libaio",
                "linux-api-headers",
                "linux-docs",
                "linux-firmware",
                "linux-headers",
                "linux-lts",
                "linux-lts-docs",
                "linux-lts-headers"
            ]
        }
    }
    ```

- `/rssthis` Turn Arch Linux package list to xml
    - `src`
        - `https://www.archlinux.org/packages/?sort=&repo=Core&q=linux-&maintainer=&flagged=`
    - `title_xpath`
        - `//*[@id="pkglist-results-form"]/table/tbody/tr/td[3]/a`
    - `link_xpath`
        - `//*[@id="pkglist-results-form"]/table/tbody/tr/td[3]/a/concat('https://archlinux.org', @href)`
    - `desc_xpath`
        - `//*[@id="pkglist-results-form"]/table/tbody/tr/td[5]/a`
    ```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <rss version="2.0">
        <channel>
            <item>
                <title>archlinux-keyring</title>
                <description>Arch Linux PGP keyring</description>
                <link>https://archlinux.org/packages/core/any/archlinux-keyring/</link>
            </item>
            <item>
                <title>libaio</title>
                <description>The Linux-native asynchronous I/O facility (aio) library</description>
                <link>https://archlinux.org/packages/core/x86_64/libaio/</link>
            </item>
            <item>
                <title>linux-api-headers</title>
                <description>Kernel headers sanitized for use in userspace</description>
                <link>https://archlinux.org/packages/core/any/linux-api-headers/</link>
            </item>
            <item>
                <title>linux-docs</title>
                <description>Documentation for the Linux kernel</description>
                <link>https://archlinux.org/packages/core/x86_64/linux-docs/</link>
            </item>
            <item>
                <title>linux-firmware</title>
                <description>Firmware files for Linux</description>
                <link>https://archlinux.org/packages/core/any/linux-firmware/</link>
            </item>
            <item>
                <title>linux-headers</title>
                <description>Headers and scripts for building modules for the Linux kernel</description>
                <link>https://archlinux.org/packages/core/x86_64/linux-headers/</link>
            </item>
            <item>
                <title>linux-lts</title>
                <description>The LTS Linux kernel and modules</description>
                <link>https://archlinux.org/packages/core/x86_64/linux-lts/</link>
            </item>
            <item>
                <title>linux-lts-docs</title>
                <description>Documentation for the LTS Linux kernel</description>
                <link>https://archlinux.org/packages/core/x86_64/linux-lts-docs/</link>
            </item>
            <item>
                <title>linux-lts-headers</title>
                <description>Headers and scripts for building modules for the LTS Linux kernel</description>
                <link>https://archlinux.org/packages/core/x86_64/linux-lts-headers/</link>
            </item>
        </channel>
    </rss>
    ```