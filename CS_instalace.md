# Instalace #

  1. Instalace aplikace na fyzickém zařízení
> > Instalační soubor PDFAnnotation.apk, který se nachází v sekci Downloads, je nutné překopírovat do fyzického zařízení, ve kterém je třeba pomocí libovolného správce souborů či instalačního asistenta aplikací soubor spustit. Po potvrzení instalačních práv nezbytných pro správný běh aplikace systém Android aplikaci automaticky nainstaluje.
  1. Instalace aplikace z digitálního úložiště Google Play
> > Aplikaci je také možné nainstalovat z digitálního úložiště aplikací Google Play po vyhledání výrazu "PDF Annotation" nebo přímo na [Google Play](https://play.google.com/store/apps/details?id=cx.pdf.android.pdfview).
  1. Instalace z běžného PC
> > a) Kompilace nativních knihoven
> > > Pro kompilaci knihoven v nativním kódu je třeba mít nainstalovaný Android NDK (Native Development Kit). Instalaci začneme spuštěním upraveného shell skriptu: ./scripts/build-native.sh. Nyní je třeba spustit skript ndk-build, který najdeme v Android NDK. Pro pohodlné kompilování je doporučeno celý adresář NDK "android-ndk-xxx" přesunout do andresáře ./scripts/ a poté spustit zmiňovaný skript ./ndk-build. Tento proces může trvat několik desítek minut. Po skončení tohoto skriptu práce s NDK končí.
      * **POZNÁMKA**
> > > > V prostředí MS Windows je doporučeno použít program cygwin, který nahrazuje terminál v operačním systému Linux.

> > b) Spuštění aplikace v prostředí Eclipse
> > > Pro správně spuštění projektu v prostředí Eclipse je třeba mít nainstalovaný doplněk Android SDK Manager a vytvořené zařízení v Android emulátoru platformy Android 2.1 a vyšší. Toto zařízení musí disponovat úložištěm pro ukládání dokumentů, ke kterému má práva zápisu.
> > > Po spuštění vývojového prostředí Eclipse je třeba vytvořit nový Android projekt, po vyplnění jména projektu a zaškrtnutí položky "vytvořit projekt z existujícího zdrojového kódu" je třeba zvolit správnou cestu ke zdrojovým kódům. Poté stačí dokončit vytvoření nového projektu.
> > > Projekt lze spustit pomocí volby spustit jako -> Android projekt a zvolit zařízení případně emulátor, na které se aplikace nainstaluje a spustí.