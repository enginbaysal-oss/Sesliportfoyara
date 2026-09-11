// fetch-remax.js
const fs = require('fs');
const puppeteer = require('puppeteer');

// Aratmak veya çekmek istediğiniz office slug veya ilan adresi
const OFFICE_SLUG = 'ilyada-3'; 
const TARGET_URL = `https://www.remax.com.tr/ofis/${OFFICE_SLUG}`;

async function fetchProperties() {
  let browser;
  try {
    console.log(`Tarayıcı başlatılıyor ve sayfaya gidiliyor: ${TARGET_URL}...`);
    
    browser = await puppeteer.launch({
      headless: "new",
      args: ['--no-sandbox', '--disable-setuid-sandbox']
    });

    const page = await browser.newPage();
    
    // Gerçek bir kullanıcı tarayıcısı gibi görünmek için User-Agent ayarı
    await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36');

    // Sayfaya git ve yüklenmesini bekle
    await page.goto(TARGET_URL, { waitUntil: 'networkidle2', timeout: 60000 });

    console.log("Sayfa yüklendi, ilan verileri toplanıyor...");

    // Sayfa içerisindeki ilan kartlarını tara
    const listings = await page.evaluate(() => {
      const items = [];
      // RE/MAX sayfasındaki ilan kartlarının DOM yapıları
      const cards = document.querySelectorAll('.property-item, .listing-card, [class*="property-card"]');

      cards.forEach(card => {
        const titleEl = card.querySelector('.title, [class*="title"], h3');
        const priceEl = card.querySelector('.price, [class*="price"]');
        const linkEl = card.querySelector('a');
        const imgEl = card.querySelector('img');
        const addressEl = card.querySelector('.location, [class*="address"], [class*="location"]');

        if (titleEl || priceEl) {
          items.push({
            id: linkEl ? linkEl.href.split('/').pop() : Math.random().toString(),
            title: titleEl ? titleEl.innerText.trim() : '',
            price: priceEl ? priceEl.innerText.trim() : '',
            address: addressEl ? addressEl.innerText.trim() : '',
            url: linkEl ? linkEl.href : '',
            mainImage: imgEl ? imgEl.src : ''
          });
        }
      });

      return items;
    });

    console.log(`Görüntülenen ilan sayısı: ${listings.length}`);

    const outputData = {
      office: OFFICE_SLUG,
      lastUpdated: new Date().toISOString(),
      count: listings.length,
      data: listings
    };

    if (!fs.existsSync('./data')) {
      fs.mkdirSync('./data');
    }

    fs.writeFileSync('./data/portfoylari_guncel.json', JSON.stringify(outputData, null, 2));
    console.log(`✅ Başarılı! Toplam ${listings.length} adet portföy kaydedildi.`);

  } catch (error) {
    console.error('❌ Hata oluştu:', error);
    process.exit(1);
  } finally {
    if (browser) await browser.close();
  }
}

fetchProperties();