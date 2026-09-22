# libs

Пусто в репозитории намеренно. Здесь должен лежать обрезанный jar Flashback, против которого идёт
компиляция. Сам Flashback распространять нельзя — его лицензия говорит прямо: «Copyright 2024
Moulberry. Do not reupload or redistribute».

Собрать его из установленной копии:

```sh
python ../icon/strip_flashback.py "путь/к/Flashback-0.39.1-for-MC1.21.8.jar"
```

Скрипт оставляет только классы `com/moulberry/**` и метаданные мода, выбрасывая ffmpeg и нативы —
из двухсот мегабайт получается около семи. Имя готового файла должно совпадать со значением
`flashback_jar` в `gradle.properties`.
