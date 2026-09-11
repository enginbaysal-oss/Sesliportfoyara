// fetch-remax.js
const fs = require('fs');

// 1. ADIM'da belirlediğiniz office slug değerini buraya yazın:
const OFFICE_SLUG = 'ilyada-3'; 
const API_URL = `https://www.remax.com.tr/api/v1/property/office/${OFFICE_SLUG}`;

async function fetchProperties() {
  try {
    console.log(`RE/MAX API'ye bağlanılıyor: ${OFFICE_SLUG}...`);
    
    const response = await fetch(API_URL, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
      }
    });

    if (!response.ok) {
      throw new Error(`RE/MAX API Bağlantı Hatası! Statü: ${response.status}`);
    }

    const json = await response.json();
    const rawListings = json.listings || [];

    // RE/MAX'tan gelen verileri kendi uygulamanızın anlayacağı formata dönüştürüyoruz
    const cleanListings = rawListings.map(item => ({
      id: item.code,                           // Örn: P43344368
      title: item.title?.[0]?.text || '',      // İlan Başlığı
      description: item.description?.[0]?.text || '', // Detaylı açıklama
      price: item.priceInfo?.amount,           // Fiyat
      currency: item.priceInfo?.amountTypeSymbol || '₺', // Para Birimi
      category: item.categoryName,             // Konut, Arsa, İşyeri vb.
      operation: item.operationName,           // Satılık / Kiralık
      city: item.address?.split('/')[0]?.trim() || '',   // İl
      town: item.address?.split('/')[1]?.trim() || '',   // İlçe
      neighborhood: item.address?.split('/')[2]?.trim() || '', // Mahalle
      address: item.address,                   // Tam Adres Metni
      m2: item.m2Area,                         // Metrekare
      roomOptions: item.roomOptions || '',     // Oda Sayısı (Örn: 3+1)
      employeeName: item.employeeName,         // İlan Danışmanı
      mainImage: item.images?.[0] || '',       // Kapak Fotoğrafı
      images: item.images || [],               // Tüm Fotoğraflar
      video: item.video || '',                 // Video Linki
      url: `https://www.remax.com.tr${item.url}`, // RE/MAX Orijinal İlan Linki
      lat: item.latitude,                      // Harita Enlem
      lng: item.longitude,                     // Harita Boylam
      updatedAt: new Date().toISOString()
    }));

    // Oluşturulacak JSON dosyasının üst yapısı
    const outputData = {
      office: OFFICE_SLUG,
      lastUpdated: new Date().toISOString(),
      count: cleanListings.length,
      data: cleanListings
    };

    // 'data' klasörüne JSON olarak kaydet
    if (!fs.existsSync('./data')) {
      fs.mkdirSync('./data');
    }
    fs.writeFileSync('./data/portfoylari_guncel.json', JSON.stringify(outputData, null, 2));
    
    console.log(`✅ Başarılı! Toplam ${cleanListings.length} adet portföy 'data/portfoylari_guncel.json' dosyasına yazıldı.`);

  } catch (error) {
    console.error('❌ Veri çekilirken hata oluştu:', error);
    process.exit(1);
  }
}

fetchProperties();