// fetch-remax.js
const fs = require('fs');
const puppeteer = require('puppeteer');

const OFFICE_SLUG = 'ilyada-3'; 
const TARGET_URL = `https://www.remax.com.tr/ofis/${OFFICE_SLUG}`;

async function fetchProperties() {
  let browser;
  try {
    console.log(`Tarayıcı başlatılıyor: ${TARGET_URL}...`);
    
    browser = await puppeteer.launch({
      headless: "new",
      args: ['--no-sandbox', '--disable-setuid-sandbox']
    });

    const page = await browser.newPage();
    await page.setViewport({ width: 1280, height: 800 });
    await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36');

    await page.goto(TARGET_URL, { waitUntil: 'domcontentloaded', timeout: 60000 });

    // Sayfanın ve ilanların tam yüklenmesi için 5 saniye bekle
    await page.waitForTimeout(5000);

    // Tüm ilanların (Lazy Loading) yüklenmesi için sayfayı aşağı kaydır
    await page.evaluate(async () => {
      await new Promise((resolve) => {
        let totalHeight = 0;
        const distance = 300;
        const timer = setInterval(() => {
          const scrollHeight = document.body.scrollHeight;
          window.scrollBy(0, distance);
          totalHeight += distance;

          if (totalHeight >= scrollHeight || totalHeight > 5000) {
            clearInterval(timer);
            resolve();
          }
        }, 200);
      });
    });

    // Sayfadaki tüm bağlantıları ve ilan yapılarını tara
    const listings = await page.evaluate(() => {
      const items = [];
      // Genişletilmiş seçici listesi
      const cards = document.querySelectorAll('a[href*="/portfoy/"], a[href*="/ilan/"], .property-item, .listing-card, [class*="PropertyCard"], [class*="listing-item"]');

      cards.forEach((card, index) => {
        const titleEl = card.querySelector('[class*="title"], [class*="Title"], h2, h3, h4') || card;
        const priceEl = card.querySelector('[class*="price"], [class*="Price"]');
        const imgEl = card.querySelector('img');
        const addressEl = card.querySelector('[class*="address"], [class*="location"], [class*="Location"]');

        const href = card.getAttribute('href') || card.querySelector('a')?.getAttribute('href') || '';
        const titleText = titleEl ? titleEl.innerText.trim() : '';

        if (href && titleText) {
          items.push({
            id: href.split('/').pop() || `item-${index}`,
            title: titleText.split('\n')[0], // Sadece ilk satırı başlık al
            price: priceEl ? priceEl.innerText.trim() : '',
            address: addressEl ? addressEl.innerText.trim() : '',
            url: href.startsWith('http') ? href : `https://www.remax.com.tr${href}`,
            mainImage: imgEl ? (imgEl.src || imgEl.getAttribute('data-src')) : ''
          });
        }
      });

      // Tekrarlayan ilanları URL'e göre temizle
      const uniqueItems = Array.from(new Map(items.map(item => [item.url, item])).values());
      return uniqueItems;
    });

    console.log(`Çekilen Net İlan Sayısı: ${listings.length}`);

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
    console.log(`✅ İşlem tamamlandı! ${listings.length} adet portföy kaydedildi.`);

  } catch (error) {
    console.error('❌ Hata oluştu:', error);
    process.exit(1);
  } finally {
    if (browser) await browser.close();
  }
}

fetchProperties();