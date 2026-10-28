REPLACEABLE IMAGES
==================

Every picture in the web app lives in this folder. The ones here are placeholders:
each one says "PLACEHOLDER IMAGE" and shows its own file name.

To use your own picture:
  1. Pick a photo (JPG or PNG). Crop it to roughly the size listed below.
  2. Save it here with EXACTLY the same file name, replacing the placeholder.
     (A JPG photo saved as "login-hero.png" still shows correctly in browsers.)
  3. Refresh the browser. If VS Code is running the app, it copies the new file
     automatically; if the old picture still shows, stop and start the app.

File                                  Used on                         Size (px)
------------------------------------  ------------------------------  ----------
logo.png                              Sidebar, login page, tab icon   256 x 256 (square)
login-hero.png                        Left side of the login page     1600 x 1100
dashboard-banner.png                  Top of the dashboard            1600 x 420
categories/prepared-meals.png         Donations in that category      800 x 500
categories/bakery.png                 "                               800 x 500
categories/fruits-and-vegetables.png  "                               800 x 500
categories/dairy.png                  "                               800 x 500
categories/meat-and-poultry.png       "                               800 x 500
categories/packaged-food.png          "                               800 x 500
categories/beverages.png              "                               800 x 500

Want a different file name or type (e.g. login.jpg)? Change the matching
<img src="/static/images/..."> in src/main/resources/templates/.
Category pictures are chosen in Donation.imageUrl() (model/Donation.java).
