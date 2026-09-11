// fetch-remax.js
const fs = require('fs');

// RE/MAX Web Arama API Endpoint'i
const API_URL = 'https://www.remax.com.tr/api/v1/property/search';

async function fetchProperties() {
  try {
    console.log("RE/MAX Arama API'sine bağlanılıyor...");

    // Arama filtreleri (Ofis adı veya anahtar kelime)
    const payload = {
      keyword: "ilyada", // Aratmak istediğiniz ofis veya danışman adı
      pageSize: 100,
      pageIndex: 1
    };

    const response = await fetch(API_URL, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Referer': 'https://www.remax.com.tr/'
      },
      body: JSON.stringify(payload)
    });

    if (!response.ok) {
      throw new Error(`RE/MAX API Bağlantı Hatası! Statü: ${response.status}`);
    }

    const json = await response.json();
    const rawListings = json.data?.items || json.listings || [];

    if (rawListings.length === 0) {
      console.warn("⚠️ Uyarı: API'den ilan dönmedi. Filtre parametrelerini kontrol edin.");
    }

    // Gelen verileri uygulamanın formatına dönüştürme
    const cleanListings = rawListings.map(item => ({
      id: item.code || item.id,
      title: item.title?.[0]?.text || item.title || '',
      description: item.description?.[0]?.text || item.description || '',
      price: item.priceInfo?.amount || item.price,
      currency: item.priceInfo?.amountTypeSymbol || item.currencySymbol || '₺',
      category: item.categoryName || item.category,
      operation: item.operationName || item.operation,
      city: item.cityName || item.address?.split('/')[0]?.trim() || '',
      town: item.townName || item.address?.split('/')[1]?.trim() || '',
      neighborhood: item.neighborhoodName || item.address?.split('/')[2]?.trim() || '',
      address: item.address || '',
      m2: item.m2Area || item.m2,
      roomOptions: item.roomOptions || item.rooms || '',
      employeeName: item.employeeName || item.agentName || '',
      mainImage: item.images?.[0] || item.coverImage || '',
      images: item.images || [],
      video: item.video || '',
      url: item.url ? `https://www.remax.com.tr${item.url}` : '',
      lat: item.latitude || item.lat,
      lng: item.longitude || item.lng,
      updatedAt: new Date().toISOString()
    }));

    const outputData = {
      lastUpdated: new Date().toISOString(),
      count: cleanListings.length,
      data: cleanListings
    };

    if (!fs.existsSync('./data')) {
      fs.mkdirSync('./data');
    }
    
    fs.writeFileSync('./data/portfoylari_guncel.json', JSON.stringify(outputData, null, 2));
    console.log(`✅ Başarılı! Toplam ${cleanListings.length} adet portföy kaydedildi.`);

  } catch (error) {
    console.error('❌ Veri çekilirken hata oluştu:', error);
    process.exit(1);
  }
}

fetchProperties();