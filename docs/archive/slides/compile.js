// compile.js - Compile all slides into final PPTX
const pptxgen = require('pptxgenjs');
const pres = new pptxgen();
pres.layout = 'LAYOUT_16x9';

const theme = {
  primary: "0a0a0a",
  secondary: "525252",
  accent: "0070F3",
  light: "D4AF37",
  bg: "f5f5f5"
};

// Load and create all slides
for (let i = 1; i <= 6; i++) {
  const num = String(i).padStart(2, '0');
  const slideModule = require(`./slide-${num}.js`);
  slideModule.createSlide(pres, theme);
}

// Write final PPTX
pres.writeFile({ fileName: './output/AgentCenter-Architecture.pptx' })
  .then(() => {
    console.log('PPTX created: ./output/AgentCenter-Architecture.pptx');
  })
  .catch(err => {
    console.error('Error creating PPTX:', err);
  });
