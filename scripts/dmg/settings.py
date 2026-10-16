# Настройки dmgbuild: приложение слева, ярлык Applications справа, фон со стрелкой между ними.
# Переменные: app (путь к Logcat.app), background (путь к bg.png; bg@2x.png рядом даёт Retina).
files = [defines["app"]]
symlinks = {"Applications": "/Applications"}
background = defines["background"]
icon_size = 128
text_size = 13
window_rect = ((200, 120), (640, 400))
icon_locations = {"Logcat.app": (170, 190), "Applications": (470, 190)}
default_view = "icon-view"
show_status_bar = False
show_tab_view = False
show_toolbar = False
show_pathbar = False
show_sidebar = False
format = "UDZO"
