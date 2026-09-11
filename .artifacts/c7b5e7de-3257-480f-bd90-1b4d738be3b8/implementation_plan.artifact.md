# RE/MAX Portföy Otomatik Çekme (Scraper) Planı

Bu plan, RE/MAX Türkiye web sitesindeki ofis sayfalarından (veya ilan detaylarından) portföy verilerini otomatik olarak çekip uygulamaya aktarmayı hedefler.

## Kullanıcı İncelemesi Gerekli

> [!IMPORTANT]
> RE/MAX web sitesi dinamik içerik (Next.js) kullandığı için veriler sayfa kaynağındaki JSON bloklarından çekilecektir. Sitenin yapısı değişirse kazıyıcının güncellenmesi gerekebilir.

## Önerilen Değişiklikler

### Ağ ve Veri Katmanı

#### [NEW] [RemaxService.kt](file:///C:/Users/engin/AndroidStudioProjects/Sesliportfoyara/app/src/commonMain/kotlin/com/example/sesliportfoyara/RemaxService.kt)
- Ktor `HttpClient` kullanarak sayfa HTML'ini çekecek.
- `<script id="__NEXT_DATA__">` etiketindeki JSON verisini ayıklayacak.
- RE/MAX veri yapısını (Next.js Props) uygulamadaki `Portfolio` modeline dönüştürecek.

### Kullanıcı Arayüzü

#### [MODIFY] [App.kt](file:///C:/Users/engin/AndroidStudioProjects/Sesliportfoyara/app/src/commonMain/kotlin/com/example/sesliportfoyara/App.kt)
- `MyPortfolioScreen` içine "RE/MAX'tan İçe Aktar" butonu eklenecek.
- Bir diyalog penceresi (AlertDialog) ile kullanıcıdan ofis veya ilan linki istenecek.
- Çekilen veriler `LocalPortfolioManager` üzerinden "Yerel" portföylere eklenecek.

## Doğrulama Planı

### Manuel Doğrulama
- Uygulama içinde `https://remax.com.tr/tr/ofis/detay/ilyada-3` linki girilerek ilanların başarıyla listelendiği ve kaydedildiği kontrol edilecek.
- İlan başlığı, fiyat, konum ve özelliklerin doğru eşleştiği teyit edilecek.
