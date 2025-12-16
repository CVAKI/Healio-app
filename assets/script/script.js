// ========================================
// DOWNLOAD FUNCTIONALITY
// ========================================

function downloadAPK() {
    console.log('Download button clicked!'); // Debug log
    
    // Show progress indicator if it exists
    const progressBar = document.getElementById('downloadProgress');
    if (progressBar) {
        progressBar.classList.add('show');
    }
    
    // Check if file exists before downloading
    fetch('main/healio.apk', { method: 'HEAD' })
        .then(response => {
            if (response.ok) {
                console.log('APK file found, starting download...');
                startDownload();
            } else {
                console.error('APK file not found at main/healio.apk');
                alert('Error: APK file not found. Please check the file path.');
            }
        })
        .catch(error => {
            console.error('Error checking APK file:', error);
            // Try download anyway
            startDownload();
        });
    
    function startDownload() {
        setTimeout(() => {
            // Create download link
            const link = document.createElement('a');
            // FIXED: Corrected path with forward slashes
            link.href = 'healio.apk'; // Your APK file path
            link.download = 'Heallio.apk'; // Downloaded filename
            link.setAttribute('type', 'application/vnd.android.package-archive');
            
            // Force download
            link.style.display = 'none';
            
            document.body.appendChild(link);
            console.log('Triggering download...'); // Debug log
            link.click();
            document.body.removeChild(link);
            
            // Hide progress and show notification
            if (progressBar) {
                progressBar.classList.remove('show');
            }
            
            setTimeout(() => {
                showDownloadNotification();
            }, 300);
        }, 1500);
    }
}

function showDownloadNotification() {
    const notification = document.getElementById('downloadNotification');
    if (notification) {
        notification.classList.add('show');
        
        // Auto-hide notification after 5 seconds
        setTimeout(() => {
            notification.classList.remove('show');
        }, 5000);
    } else {
        // Fallback: show browser alert if notification element doesn't exist
        alert('Download started! Check your downloads folder.');
    }
}

// Add event listeners to all download buttons
document.addEventListener('DOMContentLoaded', () => {
    // Select all download-related buttons
    const downloadButtons = document.querySelectorAll(
        '.btn-primary, .btn-white, .download-btn, .download-link, [href*="download"]'
    );
    
    downloadButtons.forEach(button => {
        // Check if button text contains download-related keywords
        const buttonText = button.textContent.toLowerCase();
        if (buttonText.includes('download') || buttonText.includes('launch')) {
            button.addEventListener('click', (e) => {
                e.preventDefault();
                downloadAPK();
            });
        }
    });
    
    // Close notification button
    const closeBtn = document.getElementById('closeNotification');
    if (closeBtn) {
        closeBtn.addEventListener('click', () => {
            document.getElementById('downloadNotification').classList.remove('show');
        });
    }
});

// ========================================
// NAVBAR SCROLL EFFECT
// ========================================

const navbar = document.getElementById('navbar');
const mobileMenuBtn = document.getElementById('mobileMenuBtn');

window.addEventListener('scroll', () => {
    if (window.scrollY > 50) {
        navbar.classList.add('scrolled');
    } else {
        navbar.classList.remove('scrolled');
    }
});

// ========================================
// SMOOTH SCROLLING FOR NAVIGATION
// ========================================

document.querySelectorAll('.nav-link').forEach(link => {
    link.addEventListener('click', (e) => {
        e.preventDefault();
        const targetId = link.getAttribute('href');
        const targetSection = document.querySelector(targetId);
        
        if (targetSection) {
            const offsetTop = targetSection.offsetTop - 80;
            window.scrollTo({
                top: offsetTop,
                behavior: 'smooth'
            });
        }
    });
});

// ========================================
// MOBILE MENU TOGGLE
// ========================================

let mobileMenuOpen = false;

if (mobileMenuBtn) {
    mobileMenuBtn.addEventListener('click', () => {
        mobileMenuOpen = !mobileMenuOpen;
        const navMenu = document.querySelector('.nav-menu');
        
        if (mobileMenuOpen) {
            navMenu.style.display = 'flex';
            navMenu.style.position = 'absolute';
            navMenu.style.top = '70px';
            navMenu.style.left = '0';
            navMenu.style.right = '0';
            navMenu.style.flexDirection = 'column';
            navMenu.style.background = 'white';
            navMenu.style.padding = '20px';
            navMenu.style.boxShadow = '0 4px 6px rgba(0,0,0,0.1)';
            mobileMenuBtn.innerHTML = '<i class="fas fa-times"></i>';
        } else {
            navMenu.style.display = 'none';
            mobileMenuBtn.innerHTML = '<i class="fas fa-bars"></i>';
        }
    });
}

// ========================================
// INTERSECTION OBSERVER FOR FADE-IN ANIMATIONS
// ========================================

const observerOptions = {
    threshold: 0.1,
    rootMargin: '0px 0px -100px 0px'
};

const observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
        if (entry.isIntersecting) {
            entry.target.style.opacity = '1';
            entry.target.style.transform = 'translateY(0)';
        }
    });
}, observerOptions);

// Add animation to cards
const animateElements = document.querySelectorAll(
    '.problem-card, .feature-card, .team-card, .stat-card, .process-step'
);

animateElements.forEach(el => {
    el.style.opacity = '0';
    el.style.transform = 'translateY(30px)';
    el.style.transition = 'opacity 0.6s ease, transform 0.6s ease';
    observer.observe(el);
});

// ========================================
// COUNTER ANIMATION FOR STATS
// ========================================

const animateCounter = (element, target) => {
    let current = 0;
    const increment = target / 50;
    const timer = setInterval(() => {
        current += increment;
        if (current >= target) {
            element.textContent = target;
            clearInterval(timer);
        } else {
            element.textContent = Math.floor(current);
        }
    }, 30);
};

// Observe stats for counter animation
const statsObserver = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
        if (entry.isIntersecting) {
            const statNumber = entry.target.querySelector('.stat-number');
            const text = statNumber.textContent;
            
            // Only animate if it's a pure number
            if (!isNaN(text)) {
                animateCounter(statNumber, parseInt(text));
            }
            
            statsObserver.unobserve(entry.target);
        }
    });
}, { threshold: 0.5 });

document.querySelectorAll('.stat-card').forEach(card => {
    statsObserver.observe(card);
});

// ========================================
// BUTTON CLICK RIPPLE EFFECT
// ========================================

document.querySelectorAll('.btn-white, .btn-primary, .btn-outline, .btn-outline-white').forEach(button => {
    button.addEventListener('click', (e) => {
        // Skip ripple if it's a download button (already handled)
        const buttonText = button.textContent.toLowerCase();
        if (buttonText.includes('download') || buttonText.includes('launch')) {
            return;
        }
        
        // Add ripple effect
        const ripple = document.createElement('span');
        const rect = button.getBoundingClientRect();
        const size = Math.max(rect.width, rect.height);
        const x = e.clientX - rect.left - size / 2;
        const y = e.clientY - rect.top - size / 2;
        
        ripple.style.width = ripple.style.height = size + 'px';
        ripple.style.left = x + 'px';
        ripple.style.top = y + 'px';
        ripple.style.position = 'absolute';
        ripple.style.borderRadius = '50%';
        ripple.style.background = 'rgba(255,255,255,0.5)';
        ripple.style.transform = 'scale(0)';
        ripple.style.animation = 'ripple 0.6s ease-out';
        ripple.style.pointerEvents = 'none';
        
        button.style.position = 'relative';
        button.style.overflow = 'hidden';
        button.appendChild(ripple);
        
        setTimeout(() => ripple.remove(), 600);
    });
});

// Add CSS for ripple animation
const style = document.createElement('style');
style.textContent = `
    @keyframes ripple {
        to {
            transform: scale(4);
            opacity: 0;
        }
    }
`;
document.head.appendChild(style);

// ========================================
// PARALLAX EFFECT FOR HERO SECTION
// ========================================

window.addEventListener('scroll', () => {
    const scrolled = window.pageYOffset;
    const heroContent = document.querySelector('.hero-content');
    if (heroContent && scrolled < window.innerHeight) {
        heroContent.style.transform = `translateY(${scrolled * 0.5}px)`;
        heroContent.style.opacity = 1 - (scrolled / window.innerHeight);
    }
});

// ========================================
// TEAM CARD HOVER EFFECT
// ========================================

document.querySelectorAll('.team-card').forEach(card => {
    card.addEventListener('mouseenter', () => {
        card.style.transform = 'translateY(-10px)';
    });
    
    card.addEventListener('mouseleave', () => {
        card.style.transform = 'translateY(0)';
    });
});

// ========================================
// FEATURE CARD SEQUENTIAL ANIMATION
// ========================================

const featureCards = document.querySelectorAll('.feature-card');
const featureObserver = new IntersectionObserver((entries) => {
    entries.forEach((entry, index) => {
        if (entry.isIntersecting) {
            setTimeout(() => {
                entry.target.style.opacity = '1';
                entry.target.style.transform = 'translateY(0)';
            }, index * 100);
            featureObserver.unobserve(entry.target);
        }
    });
}, { threshold: 0.2 });

featureCards.forEach(card => {
    card.style.opacity = '0';
    card.style.transform = 'translateY(30px)';
    card.style.transition = 'opacity 0.5s ease, transform 0.5s ease';
    featureObserver.observe(card);
});

// ========================================
// PROCESS STEPS ANIMATION
// ========================================

const processSteps = document.querySelectorAll('.process-step');
const processObserver = new IntersectionObserver((entries) => {
    entries.forEach((entry, index) => {
        if (entry.isIntersecting) {
            setTimeout(() => {
                entry.target.style.opacity = '1';
                entry.target.style.transform = 'scale(1)';
            }, index * 150);
            processObserver.unobserve(entry.target);
        }
    });
}, { threshold: 0.3 });

processSteps.forEach(step => {
    step.style.opacity = '0';
    step.style.transform = 'scale(0.8)';
    step.style.transition = 'opacity 0.5s ease, transform 0.5s ease';
    processObserver.observe(step);
});

// ========================================
// ACTIVE SECTION TRACKING
// ========================================

const sections = document.querySelectorAll('section[id]');
const navLinks = document.querySelectorAll('.nav-link');

window.addEventListener('scroll', () => {
    let current = '';
    
    sections.forEach(section => {
        const sectionTop = section.offsetTop;
        const sectionHeight = section.clientHeight;
        if (pageYOffset >= sectionTop - 200) {
            current = section.getAttribute('id');
        }
    });
    
    navLinks.forEach(link => {
        link.classList.remove('active');
        if (link.getAttribute('href') === `#${current}`) {
            link.classList.add('active');
        }
    });
});

// Add active link style
const activeLinkStyle = document.createElement('style');
activeLinkStyle.textContent = `
    .nav-link.active {
        color: #A2CFEC !important;
    }
    
    #navbar.scrolled .nav-link.active {
        color: #2563eb !important;
    }
`;
document.head.appendChild(activeLinkStyle);

// ========================================
// LAZY LOADING FOR IMAGES
// ========================================

if ('loading' in HTMLImageElement.prototype) {
    const images = document.querySelectorAll('img[loading="lazy"]');
    images.forEach(img => {
        img.src = img.dataset.src;
    });
} else {
    // Fallback for browsers that don't support lazy loading
    const script = document.createElement('script');
    script.src = 'https://cdnjs.cloudflare.com/ajax/libs/lazysizes/5.3.2/lazysizes.min.js';
    document.body.appendChild(script);
}

// ========================================
// CONSOLE WELCOME MESSAGE
// ========================================

console.log('%c🚑 Welcome to Heallio! ', 'background: #2563eb; color: white; font-size: 20px; padding: 10px;');
console.log('%cSaving lives through offline emergency response technology', 'color: #4A90E2; font-size: 14px;');

// ========================================
// INITIALIZE ON DOM LOAD
// ========================================

document.addEventListener('DOMContentLoaded', () => {
    console.log('Heallio website initialized successfully!');
    
    // Add smooth page load animation
    document.body.style.opacity = '0';
    setTimeout(() => {
        document.body.style.transition = 'opacity 0.5s ease';
        document.body.style.opacity = '1';
    }, 100);

});
