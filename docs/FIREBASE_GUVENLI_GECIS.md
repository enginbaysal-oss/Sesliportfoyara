# Firebase Realtime Database güvenli geçiş — 2 Kasım 2026

## Doğrulanan durum
- Firebase projesi: `sesliaraportfoy`, RTDB: `sesliaraportfoy-default-rtdb`.
- Kullanıcı Firebase kurallarını `.read=false`, `.write=false` olarak yayımladı. **Güvenliği düşürmeyin.**
- Kullanıcının sağladığı JSON dışa aktarımında yalnızca `listings` (142), `portfolio_progress` (17), `emlak_asistan_archives` (8) üst düzey kayıtları var. Yedek kişisel bilgiler içerir; depoya commit edilmemelidir.
- Firebase Authentication konsolunda kullanıcı bulunmuyor.
- Android `MainActivity.kt`, Supabase `/auth/v1/token?grant_type=password` üzerinden e-posta/şifreyle oturum açıyor.
- Aynı dosyada `registration_requests.json` yoluna belirteçsiz doğrudan HTTP POST/GET/DELETE mevcut. Kapalı RTDB kuralları altında bu çağrılar reddedilir.
- Web sürümünün portföy sorgularında 401 görüldüğü kullanıcı konsol ekranıyla doğrulandı; tüm web kaynak kodu ve kimlik akışı henüz tam denetlenmedi.

## Güvenli uygulama sırası
1. Dışa aktarılan JSON'u yalnızca özel, erişim kontrollü bir yedek konumunda saklayın; bütünlüğünü kontrol edin. Kullanıcı kayıtları bu yedekte yok.
2. Web/Android Firebase erişimlerini ve bunların işlevlerini envanterleyin; özellikle `listings`, `portfolio_progress`, `emlak_asistan_archives`, `registration_requests`, `authorized_users`.
3. Mevcut Supabase oturumunu sunucuda doğrulayan güvenli bir API/Edge Function kullanın. Ofis üyeliği ve rol yetkisini sunucuda denetleyin. **Firebase yönetici anahtarı / service-account yalnızca sunucu sırlarında kalmalı.**
4. RTDB verilerine sunucudan Firebase Admin SDK veya uygun yönetici kimliğiyle erişin; istemciler doğrudan açık REST uçlarına erişmesin.
5. Ofis bazlı listeleme, tek kayıt, oluşturma, güncelleme, silme için ayrı yetki kuralları uygulayın. Kayıt sahipliği ve ofis kimliği sunucuda doğrulansın; istemciye güvenilmesin.
6. Web ve Android istemcilerini kademeli olarak yeni API'ye taşıyın; eski giriş ve veri formatlarını koruyun. Gerçek kullanıcılarla test edilmeden üretim erişimini değiştirmeyin.
7. Yetkili kullanıcı, farklı ofis kullanıcısı ve giriş yapmamış kullanıcı için olumlu/olumsuz erişim testleri yapın. 401/403 hatalarını ve ofis portföylerini kontrol edin.

## Yapılmaması gerekenler
- `.read=true` veya `.write=true` kurallarına geri dönmek.
- Supabase JWT'yi Firebase RTDB `auth` olarak varsaymak.
- Firebase service-account özel anahtarını GitHub'a veya istemci koduna koymak.
- Yedek tamamlanmadan veritabanını taşımak veya silmek.

Bu belge bir uygulama planıdır; henüz canlı sistemde veri taşıma veya erişim düzeltmesi yapılmadı.
