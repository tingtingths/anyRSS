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
- `/xeva`  
    src - `https://www.archlinux.org/packages/?sort=&repo=Core&q=linux-&maintainer=&flagged=`  
    xpath - `//*[@id="pkglist-results-form"]/table/tbody/tr/td[3]/a`
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
