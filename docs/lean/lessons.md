# Lessons

What agents keep rediscovering about this project, one fact per line with its evidence. Hints to
check, never proof. People write this file; delete a line that turns out wrong.

- The adaptive launcher icon must stay in `res/mipmap-anydpi-v26/`; lint suggests `mipmap-anydpi`, but aapt2 then cannot resolve `mipmap/ic_launcher` (setup commit 7143a79).
- The box site's red `#E5484D` fails 4.5:1 on Night; use `GuardColors.Urgent` (`#F07478`) for red text (ContrastTest, setup).
