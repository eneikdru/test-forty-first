<script>
  import { onMount } from 'svelte';

  // 1. Core States
  let user = null; // Mapped to { username, role, fullName } after login
  let loginUsername = "ivan.ivanov@epidem.ru";
  let loginPassword = "securePassword123";
  let loginRole = "Administrator"; // Default pre-select for easier UX
  let loginError = "";

  // Available roles for demonstration
  const ROLES = [
    { value: "Administrator", label: "Администратор" },
    { value: "Content-manager", label: "Контент-менеджер" },
    { value: "Teacher", label: "Преподаватель / научный руководитель" },
    { value: "Student", label: "Ординатор / аспирант / слушатель" },
    { value: "Economist", label: "Экономист" }
  ];

  // 2. Search & Filter State
  let q = "";
  let selectedDocType = "";
  let selectedSpecialty = "";
  let selectedEduLevel = "";
  let selectedTag = "";
  let activeTab = "search"; // "search" or "library" (favorites)

  // 3. UI interaction states
  let searchSuggestions = [];
  let showSuggestions = false;
  let selectedDocument = null;
  let isOffline = false; // Emulated or real offline mode
  let actualizationReason = "";
  let actualizationSuccess = false;
  let newCommentText = "";

  // 6. Editing States
  let isEditing = false;
  let editName = "";
  let editDescription = "";
  let editError = "";
  let editSuccess = "";

  $: isBudgetFile = selectedDocument && (selectedDocument.category_id === "edu_budget_finance" || selectedDocument.tags.includes("бюджет") || selectedDocument.tags.includes("Budget") || selectedDocument.name.toLowerCase().includes("бюджет") || selectedDocument.name.toLowerCase().includes("budget"));
  $: accessDeniedToSelected = user && user.role === "Student" && isBudgetFile;

  // 4. Persistence / Caching States (Loaded from LocalStorage)
  let documents = [];
  let favorites = [];
  let savedSearches = [];

  // 5. Hardcoded Abbreviations and Synonyms Dictionary
  const ABBREVIATIONS = {
    "ФБУН": "ФБУН ЦНИИ Эпидемиологии Роспотребнадзора",
    "ГЭК": "Государственная экзаменационная комиссия",
    "ГИА": "Государственная итоговая аттестация",
    "ФГОС": "Федеральный государственный образовательный стандарт"
  };

  // Seed documents data if local storage is empty
  const SEED_DOCUMENTS = [
    {
      id: "9a2fbb22-c35d-4f11-92b1-50e58f00032b",
      name: "ФГОС ВО по специальности Эпидемиология",
      description: "Федеральный государственный образовательный стандарт высшего образования по направлению Эпидемиология (ординатура). Обязателен для всех учебных программ кафедры.",
      doc_type: "Regulations",
      specialty: "Epidemiology",
      edu_level: "Residency",
      category_id: "edu_center_root",
      tags: ["ординатура", "нормативные акты", "ФГОС"],
      version: 3,
      fileSize: 245000,
      fileType: "application/pdf",
      updatedAt: "2026-08-01T10:00:00Z",
      updatedBy: "ivan.ivanov@epidem.ru"
    },
    {
      id: "c3bd81a2-e304-4b55-a289-4b62f4001a1c",
      name: "Шаблон протокола ГЭК для ГИА",
      description: "Официальный шаблон протокола заседания государственной экзаменационной комиссии. Применяется для оформления результатов ГИА.",
      doc_type: "Forms/Templates",
      specialty: "Other",
      edu_level: "Residency",
      category_id: "edu_academic_reports",
      tags: ["шаблоны", "ГЭК", "ГИА", "ординатура"],
      version: 1,
      fileSize: 125000,
      fileType: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      updatedAt: "2026-07-15T14:30:00Z",
      updatedBy: "elena.petrova@epidem.ru"
    },
    {
      id: "8fbc923d-4c5a-4fdf-91bf-a3c309503a4b",
      name: "Методические рекомендации по детским инфекционным болезням",
      description: "Методические материалы по специальности Педиатрия. Включает клинические кейсы, планы занятий и перечень компетенций ФГОС.",
      doc_type: "Guidelines",
      specialty: "Pediatrics",
      edu_level: "Additional Professional Education",
      category_id: "edu_center_root",
      tags: ["педиатрия", "инструкции", "рекомендации"],
      version: 2,
      fileSize: 412000,
      fileType: "application/pdf",
      updatedAt: "2026-07-28T09:15:00Z",
      updatedBy: "ivan.ivanov@epidem.ru"
    },
    {
      id: "3e4f5a6b-7c8d-9e0f-1a2b-3c4d5e6f7a8b",
      name: "Вопросы к кандидатскому экзамену по специальности Инфекционные болезни",
      description: "Перечень теоретических вопросов и практических заданий для подготовки к кандидатскому экзамену по инфекционным болезням аспирантов.",
      doc_type: "Protocols",
      specialty: "Infectious Diseases",
      edu_level: "Postgraduate",
      category_id: "edu_academic_reports",
      tags: ["аспирантура", "вопросы к экзаменам", "аттестация"],
      version: 4,
      fileSize: 189000,
      fileType: "application/pdf",
      updatedAt: "2026-08-02T16:45:00Z",
      updatedBy: "sergey.smirnov@epidem.ru"
    },
    {
      id: "fa32b451-8d23-4411-9a4c-d10002ab922c",
      name: "Регламент интеграции учебных планов в СЭД ФБУН",
      description: "Регламент и пошаговые инструкции по выгрузке данных в СЭД образовательного центра ФБУН ЦНИИ Эпидемиологии Роспотребнадзора.",
      doc_type: "Regulations",
      specialty: "Other",
      edu_level: "Additional Professional Education",
      category_id: "edu_staff_workload",
      tags: ["ФБУН", "инструкции", "регламент"],
      version: 2,
      fileSize: 310000,
      fileType: "application/pdf",
      updatedAt: "2026-06-20T11:00:00Z",
      updatedBy: "ivan.ivanov@epidem.ru"
    },
    {
      id: "b45a6b7c-8d9e-0f1a-2b3c-4d5e6f7a8bc9",
      name: "Годовой финансовый бюджет центра на 2026 год",
      description: "Детализированный годовой финансовый план, статьи расходов, распределение бюджетного финансирования кафедры.",
      doc_type: "Regulations",
      specialty: "Other",
      edu_level: "Residency",
      category_id: "edu_budget_finance",
      tags: ["ординатура", "нормативные акты", "бюджет", "финансы"],
      version: 1,
      fileSize: 450000,
      fileType: "application/pdf",
      updatedAt: "2026-08-01T10:00:00Z",
      updatedBy: "economist@epidem.ru"
    }
  ];

  // Mock comments database by document id
  let commentsDb = {
    "9a2fbb22-c35d-4f11-92b1-50e58f00032b": [
      { id: "c1", user: "Елена Петрова (Контент-менеджер)", text: "Стандарт актуализирован согласно приказу Минздрава РФ.", createdAt: "2026-08-01T11:00:00Z" },
      { id: "c2", user: "Иван Иванов (Преподаватель)", text: "Отлично, рабочие программы ординатуры будут скорректированы в соответствии с этой версией.", createdAt: "2026-08-01T12:30:00Z" }
    ],
    "c3bd81a2-e304-4b55-a289-4b62f4001a1c": [
      { id: "c3", user: "Иван Иванов (Администратор)", text: "Пожалуйста, используйте этот шаблон для всех заседаний ГЭК в этом семестре.", createdAt: "2026-07-16T10:00:00Z" }
    ]
  };

  onMount(() => {
    // Check local storage for documents
    const savedDocs = localStorage.getItem("lexicon_documents");
    if (savedDocs) {
      const parsed = JSON.parse(savedDocs);
      if (!parsed.some(d => d.id === "b45a6b7c-8d9e-0f1a-2b3c-4d5e6f7a8bc9")) {
        documents = [...parsed, ...SEED_DOCUMENTS.filter(d => d.id === "b45a6b7c-8d9e-0f1a-2b3c-4d5e6f7a8bc9")];
        localStorage.setItem("lexicon_documents", JSON.stringify(documents));
      } else {
        documents = parsed;
      }
    } else {
      documents = SEED_DOCUMENTS;
      localStorage.setItem("lexicon_documents", JSON.stringify(SEED_DOCUMENTS));
    }

    // Check favorites
    const savedFavs = localStorage.getItem("lexicon_favorites");
    if (savedFavs) {
      favorites = JSON.parse(savedFavs);
    }

    // Check saved searches
    const savedQueries = localStorage.getItem("lexicon_saved_searches");
    if (savedQueries) {
      savedSearches = JSON.parse(savedQueries);
    }

    // Load session user if logged in
    const sessionUser = localStorage.getItem("lexicon_user");
    if (sessionUser) {
      user = JSON.parse(sessionUser);
    }

    // Listen to network changes
    isOffline = !navigator.onLine;
    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);

    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  });

  function handleOnline() {
    isOffline = false;
  }

  function handleOffline() {
    isOffline = true;
  }

  // Handle Login authentication
  function handleLogin(e) {
    e.preventDefault();
    if (!loginUsername) {
      loginError = "Введите имя пользователя или корпоративный email";
      return;
    }
    if (!loginPassword) {
      loginError = "Введите пароль";
      return;
    }

    // Create a mock user
    const loggedUser = {
      username: loginUsername,
      role: loginRole,
      fullName: loginUsername.includes("ivan") ? "Иванов Иван Иванович" : "Петров Петр Петрович"
    };

    user = loggedUser;
    localStorage.setItem("lexicon_user", JSON.stringify(loggedUser));
    loginError = "";
  }

  // Handle Logout
  function handleLogout() {
    user = null;
    localStorage.removeItem("lexicon_user");
    selectedDocument = null;
  }

  // Search autocomplete / suggestion updates
  function handleSearchInput() {
    if (!q) {
      searchSuggestions = [];
      showSuggestions = false;
      return;
    }

    const queryLower = q.toLowerCase();

    // Generate suggestions based on titles, tags, and abbreviation matches
    const matches = [];
    documents.forEach(doc => {
      if (doc.name.toLowerCase().includes(queryLower)) {
        matches.push(doc.name);
      }
      doc.tags.forEach(tag => {
        if (tag.toLowerCase().includes(queryLower) && !matches.includes(tag)) {
          matches.push(tag);
        }
      });
    });

    // Suggest abbreviation expansions if typed partial matches
    Object.keys(ABBREVIATIONS).forEach(abbr => {
      if (abbr.toLowerCase().includes(queryLower) || ABBREVIATIONS[abbr].toLowerCase().includes(queryLower)) {
        const suggestion = `${abbr} (${ABBREVIATIONS[abbr]})`;
        if (!matches.includes(suggestion)) {
          matches.push(suggestion);
        }
      }
    });

    searchSuggestions = matches.slice(0, 5);
    showSuggestions = searchSuggestions.length > 0;
  }

  function selectSuggestion(suggestion) {
    // Extract abbreviation if present in brackets
    if (suggestion.includes(" (")) {
      q = suggestion.split(" (")[0];
    } else {
      q = suggestion;
    }
    showSuggestions = false;
    handleSearchInput();
  }

  // Save the current search query and filters
  function saveSearchQuery() {
    if (!q && !selectedDocType && !selectedSpecialty && !selectedEduLevel) {
      return;
    }
    const queryName = q ? `Поиск: ${q}` : "Фильтрованный поиск";
    const newSave = {
      id: "q-" + Date.now(),
      queryName: queryName,
      q: q,
      doc_type: selectedDocType,
      specialty: selectedSpecialty,
      edu_level: selectedEduLevel,
      createdAt: new Date().toISOString()
    };

    savedSearches = [newSave, ...savedSearches];
    localStorage.setItem("lexicon_saved_searches", JSON.stringify(savedSearches));
  }

  // Load a previously saved search query
  function applySavedSearch(saved) {
    q = saved.q || "";
    selectedDocType = saved.doc_type || "";
    selectedSpecialty = saved.specialty || "";
    selectedEduLevel = saved.edu_level || "";
    activeTab = "search";
  }

  // Delete a saved search query
  function deleteSavedSearch(id) {
    savedSearches = savedSearches.filter(s => s.id !== id);
    localStorage.setItem("lexicon_saved_searches", JSON.stringify(savedSearches));
  }

  // Toggle bookmarked Favorite status
  function toggleFavorite(docId) {
    if (favorites.includes(docId)) {
      favorites = favorites.filter(id => id !== docId);
    } else {
      favorites = [...favorites, docId];
    }
    localStorage.setItem("lexicon_favorites", JSON.stringify(favorites));
  }

  // Search and Filter logic
  $: filteredDocuments = documents.filter(doc => {
    // Filter by Tab (all vs favorites)
    if (activeTab === "library" && !favorites.includes(doc.id)) {
      return false;
    }

    // Text search filter supporting synonyms/abbreviations
    if (q) {
      const queryLower = q.toLowerCase().trim();
      let match = doc.name.toLowerCase().includes(queryLower) || doc.description.toLowerCase().includes(queryLower);

      // Check abbreviation expansion matches
      Object.keys(ABBREVIATIONS).forEach(abbr => {
        if (queryLower.includes(abbr.toLowerCase()) || abbr.toLowerCase().includes(queryLower)) {
          // If query mentions ФГОС, also match documents having tag/title containing standard expansion or standard word
          const expanded = ABBREVIATIONS[abbr].toLowerCase();
          if (doc.name.toLowerCase().includes(expanded) || doc.description.toLowerCase().includes(expanded) || doc.tags.includes(abbr.toLowerCase())) {
            match = true;
          }
        }
      });

      // Check tags
      if (doc.tags.some(tag => tag.toLowerCase().includes(queryLower))) {
        match = true;
      }

      if (!match) return false;
    }

    // Dropdown filters
    if (selectedDocType && doc.doc_type !== selectedDocType) return false;
    if (selectedSpecialty && doc.specialty !== selectedSpecialty) return false;
    if (selectedEduLevel && doc.edu_level !== selectedEduLevel) return false;

    // Tag filter chip
    if (selectedTag && !doc.tags.includes(selectedTag)) return false;

    return true;
  });

  // Open document details drawer
  function openDocumentDetails(doc) {
    selectedDocument = doc;
    actualizationReason = "";
    actualizationSuccess = false;
    newCommentText = "";
    isEditing = false;
    editName = doc.name;
    editDescription = doc.description;
    editError = "";
    editSuccess = "";
  }

  function handleEditClick() {
    if (user && user.role === "Student") {
      editError = "Доступ запрещен. Ординаторы, аспиранты и слушатели не имеют прав на редактирование материалов.";
      editSuccess = "";
      return;
    }
    isEditing = true;
    editError = "";
    editSuccess = "";
    editName = selectedDocument.name;
    editDescription = selectedDocument.description;
  }

  function handleSaveEdit() {
    if (!editName.trim() || !editDescription.trim()) {
      editError = "Пожалуйста, заполните все обязательные поля.";
      return;
    }
    if (user && user.role === "Student") {
      editError = "Доступ запрещен. Ординаторы, аспиранты и слушатели не имеют прав на редактирование материалов.";
      return;
    }

    // Update document in list
    documents = documents.map(d => {
      if (d.id === selectedDocument.id) {
        return {
          ...d,
          name: editName,
          description: editDescription,
          version: d.version + 1,
          updatedAt: new Date().toISOString(),
          updatedBy: user.username
        };
      }
      return d;
    });

    localStorage.setItem("lexicon_documents", JSON.stringify(documents));

    // Update selectedDocument details in UI
    selectedDocument = documents.find(d => d.id === selectedDocument.id);
    isEditing = false;
    editSuccess = "Документ успешно сохранен и обновлен.";
    editError = "";
  }

  function handleCancelEdit() {
    isEditing = false;
    editError = "";
  }

  // Post a new comment
  function postComment() {
    if (!newCommentText || isOffline) return;

    const newComment = {
      id: "c-" + Date.now(),
      user: `${user.fullName} (${getRoleLabel(user.role)})`,
      text: newCommentText,
      createdAt: new Date().toISOString()
    };

    const docComments = commentsDb[selectedDocument.id] || [];
    commentsDb[selectedDocument.id] = [...docComments, newComment];
    newCommentText = "";

    // Re-trigger Svelte reactive rendering for selectedDocument
    selectedDocument = { ...selectedDocument };
  }

  // Send request for document actualization
  function sendActualizationRequest() {
    if (!actualizationReason || isOffline) return;

    // Simulate API request to "/documents/{id}/actualization-request"
    actualizationSuccess = true;
    actualizationReason = "";
  }

  // Get localized document type translation
  function getDocTypeLabel(type) {
    switch (type) {
      case "Regulations": return "Регламенты / Нормативные акты";
      case "Forms/Templates": return "Формы / Шаблоны";
      case "Protocols": return "Протоколы";
      case "Curriculum": return "Учебные планы";
      case "Guidelines": return "Методические рекомендации";
      default: return type;
    }
  }

  // Get localized specialty translation
  function getSpecialtyLabel(spec) {
    switch (spec) {
      case "Epidemiology": return "Эпидемиология";
      case "Infectious Diseases": return "Инфекционные болезни";
      case "Pediatrics": return "Педиатрия";
      case "Other": return "Другое";
      default: return spec;
    }
  }

  // Get localized edu level translation
  function getEduLevelLabel(level) {
    switch (level) {
      case "Residency": return "Ординатура";
      case "Postgraduate": return "Аспирантура";
      case "Additional Professional Education": return "Доп. проф. образование";
      default: return level;
    }
  }

  // Get localized role translation
  function getRoleLabel(role) {
    const matched = ROLES.find(r => r.value === role);
    return matched ? matched.label : role;
  }

  // Format File Size
  function formatBytes(bytes) {
    if (bytes === 0) return '0 Байт';
    const k = 1024;
    const sizes = ['Байт', 'КБ', 'МБ'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  }

  // Format Date ISO
  function formatDate(isoString) {
    const d = new Date(isoString);
    return d.toLocaleDateString("ru-RU", { day: "numeric", month: "long", year: "numeric" });
  }

  // Simulate manual toggle offline mode
  function toggleOfflineEmulation() {
    isOffline = !isOffline;
  }
</script>

<div class="min-h-screen text-[#d4e4fa] font-body-sm relative">

  <!-- TOP BAR -->
  <header class="fixed top-0 w-full z-40 border-b border-outline-variant bg-surface-dim/95 backdrop-blur-md h-16 flex justify-between items-center px-4 md:px-8">
    <div class="flex items-center gap-3">
      <span class="material-symbols-outlined text-primary text-2xl" data-icon="terminal">terminal</span>
      <h1 class="font-headline-md text-xl md:text-2xl font-bold text-primary tracking-tight">Lexicon Flux</h1>
    </div>

    {#if user}
      <div class="flex items-center gap-4">
        <div class="hidden sm:flex flex-col text-right">
          <span class="font-semibold text-sm">{user.fullName}</span>
          <span class="text-xs text-on-surface-variant font-medium">{getRoleLabel(user.role)}</span>
        </div>

        <!-- Offline Emulator Switch -->
        <button
          on:click={toggleOfflineEmulation}
          class="flex items-center gap-1.5 px-3 py-1.5 rounded-sm border transition-colors font-medium text-xs duration-200"
          class:border-red-500={isOffline}
          class:text-red-400={isOffline}
          class:bg-red-950={isOffline}
          class:bg-opacity-30={isOffline}
          class:border-outline-variant={!isOffline}
          class:text-on-surface-variant={!isOffline}
          aria-label="Переключить офлайн режим"
        >
          <span class="material-symbols-outlined text-sm">{isOffline ? 'cloud_off' : 'cloud_queue'}</span>
          <span>{isOffline ? 'Офлайн-симуляция ON' : 'Сеть OK'}</span>
        </button>

        <button
          on:click={handleLogout}
          class="flex items-center gap-1 px-3 py-1.5 bg-surface-variant hover:bg-surface-container-highest text-[#d4e4fa] font-semibold text-xs rounded transition-colors"
          aria-label="Выйти"
        >
          <span class="material-symbols-outlined text-sm">logout</span>
          <span class="hidden md:inline">Выйти</span>
        </button>
      </div>
    {/if}
  </header>

  <!-- OFFLINE STATUS BANNER -->
  {#if isOffline}
    <div class="fixed top-16 left-0 w-full bg-red-950 border-b border-red-800 text-red-200 px-4 py-2 text-center text-xs sm:text-sm font-semibold z-30 flex items-center justify-center gap-2 animate-pulse">
      <span class="material-symbols-outlined text-base">wifi_off</span>
      <span>Вы работаете в автономном режиме. Доступны только кэшированные материалы и сохраненные поиски.</span>
    </div>
  {/if}

  <!-- MAIN CONTAINER (Dynamically paddings to prevent fixed overlap with header and offline banner) -->
  <main
    class="pb-20 px-4 md:px-8 max-w-[1440px] mx-auto min-h-screen transition-all duration-300"
    class:pt-36={isOffline && user}
    class:pt-24={!(isOffline && user)}
  >

    {#if !user}
      <!-- LOGIN PAGE (Strictly Russian and styled beautifully to prevent CLS) -->
      <div class="min-h-[600px] flex items-center justify-center py-12 px-4">
        <div class="w-full max-w-md bg-surface-container border border-outline-variant p-8 rounded shadow-2xl space-y-6">
          <div class="text-center space-y-2">
            <span class="material-symbols-outlined text-primary text-5xl" data-icon="school">school</span>
            <h2 class="text-2xl font-bold text-primary tracking-tight">Образовательный центр</h2>
            <p class="text-sm text-on-surface-variant">База знаний ФБУН ЦНИИ Эпидемиологии</p>
          </div>

          {#if loginError}
            <div class="bg-red-950 bg-opacity-40 border border-red-500 text-red-200 p-3 rounded text-sm font-medium">
              {loginError}
            </div>
          {/if}

          <form on:submit={handleLogin} class="space-y-4">
            <div>
              <label for="username" class="block text-xs font-bold text-[#d4e4fa] uppercase tracking-wider mb-2">
                Имя пользователя (Корпоративная почта)
              </label>
              <input
                id="username"
                type="text"
                bind:value={loginUsername}
                class="w-full bg-surface-container-lowest border border-outline-variant rounded p-3 text-sm focus:border-primary focus:ring-1 focus:ring-primary"
                placeholder="иван.иванов@epidem.ru"
              />
            </div>

            <div>
              <label for="password" class="block text-xs font-bold text-[#d4e4fa] uppercase tracking-wider mb-2">
                Пароль
              </label>
              <input
                id="password"
                type="password"
                bind:value={loginPassword}
                class="w-full bg-surface-container-lowest border border-outline-variant rounded p-3 text-sm focus:border-primary focus:ring-1 focus:ring-primary"
                placeholder="••••••••"
              />
            </div>

            <div>
              <label for="role" class="block text-xs font-bold text-[#d4e4fa] uppercase tracking-wider mb-2">
                Ваша роль для тестирования
              </label>
              <select
                id="role"
                bind:value={loginRole}
                class="w-full bg-surface-container-lowest border border-outline-variant rounded p-3 text-sm focus:border-primary focus:ring-1 focus:ring-primary text-on-surface"
              >
                {#each ROLES as roleOpt}
                  <option value={roleOpt.value}>{roleOpt.label}</option>
                {/each}
              </select>
            </div>

            <button
              type="submit"
              class="w-full py-3 bg-primary text-on-primary-fixed font-bold rounded hover:bg-opacity-90 transition-all active:scale-[0.99] min-h-[44px]"
            >
              Войти в систему
            </button>
          </form>

          <div class="text-center">
            <span class="text-xs text-on-surface-variant">Поддерживается авторизация по логину/паролю и SSO ЦНИИ</span>
          </div>
        </div>
      </div>

    {:else}
      <!-- DASHBOARD - SEARCH & VIEW (Strictly Russian and responsive) -->
      <div class="grid grid-cols-1 lg:grid-cols-4 gap-8">

        <!-- SIDEBAR (Filters & Saved Searches) -->
        <aside class="space-y-6 lg:col-span-1" aria-label="Панель фильтров">

          <!-- Document Filters Container -->
          <div class="bg-surface-container border border-outline-variant p-5 rounded space-y-4">
            <div class="flex items-center justify-between border-b border-outline-variant pb-3 mb-2">
              <h3 class="font-bold text-sm text-[#d4e4fa] tracking-wide uppercase">Фильтрация документов</h3>
              <button
                on:click={() => { selectedDocType = ""; selectedSpecialty = ""; selectedEduLevel = ""; selectedTag = ""; q = ""; }}
                class="text-xs text-primary hover:underline"
              >
                Сбросить
              </button>
            </div>

            <!-- Doc Type Filter -->
            <div>
              <label for="filter-doc-type" class="block text-xs font-bold uppercase text-on-surface-variant mb-1.5">Тип документа</label>
              <select
                id="filter-doc-type"
                bind:value={selectedDocType}
                class="w-full bg-surface-container-lowest border border-outline-variant text-sm rounded p-2 focus:border-primary focus:ring-0 text-on-surface"
              >
                <option value="">Все типы</option>
                <option value="Regulations">Регламенты / Нормативные акты</option>
                <option value="Forms/Templates">Формы и шаблоны документов</option>
                <option value="Protocols">Протоколы</option>
                <option value="Curriculum">Учебно-методические материалы</option>
                <option value="Guidelines">Методические рекомендации</option>
              </select>
            </div>

            <!-- Specialty Filter -->
            <div>
              <label for="filter-specialty" class="block text-xs font-bold uppercase text-on-surface-variant mb-1.5">Специальность</label>
              <select
                id="filter-specialty"
                bind:value={selectedSpecialty}
                class="w-full bg-surface-container-lowest border border-outline-variant text-sm rounded p-2 focus:border-primary focus:ring-0 text-on-surface"
              >
                <option value="">Все специальности</option>
                <option value="Epidemiology">Эпидемиология</option>
                <option value="Infectious Diseases">Инфекционные болезни</option>
                <option value="Pediatrics">Педиатрия</option>
                <option value="Other">Другие смежные</option>
              </select>
            </div>

            <!-- Education Level Filter -->
            <div>
              <label for="filter-edu-level" class="block text-xs font-bold uppercase text-on-surface-variant mb-1.5">Уровень образования</label>
              <select
                id="filter-edu-level"
                bind:value={selectedEduLevel}
                class="w-full bg-surface-container-lowest border border-outline-variant text-sm rounded p-2 focus:border-primary focus:ring-0 text-on-surface"
              >
                <option value="">Все уровни</option>
                <option value="Residency">Ординатура</option>
                <option value="Postgraduate">Аспирантура</option>
                <option value="Additional Professional Education">Доп. проф. образование</option>
              </select>
            </div>
          </div>

          <!-- Saved Searches Section -->
          <div class="bg-surface-container border border-outline-variant p-5 rounded space-y-4">
            <div class="flex items-center justify-between border-b border-outline-variant pb-3 mb-2">
              <h3 class="font-bold text-sm text-[#d4e4fa] tracking-wide uppercase">Сохраненные поиски</h3>
              {#if q || selectedDocType || selectedSpecialty || selectedEduLevel}
                <button
                  on:click={saveSearchQuery}
                  class="text-xs text-primary hover:underline flex items-center gap-0.5"
                  aria-label="Сохранить текущие фильтры поиска"
                >
                  <span class="material-symbols-outlined text-xs">bookmark</span>
                  Сохранить
                </button>
              {/if}
            </div>

            {#if savedSearches.length === 0}
              <p class="text-xs text-on-surface-variant text-center py-2">Нет сохраненных поисков</p>
            {:else}
              <ul class="space-y-2">
                {#each savedSearches as saved}
                  <li class="flex items-center justify-between gap-2 p-2 bg-surface-container-low border border-outline-variant rounded">
                    <button
                      on:click={() => applySavedSearch(saved)}
                      class="text-xs text-left font-semibold text-[#d4e4fa] hover:text-primary hover:underline truncate flex-1"
                    >
                      {saved.queryName}
                    </button>
                    <button
                      on:click={() => deleteSavedSearch(saved.id)}
                      class="text-on-surface-variant hover:text-red-400 transition-colors"
                      aria-label="Удалить сохраненный поиск"
                    >
                      <span class="material-symbols-outlined text-xs">delete</span>
                    </button>
                  </li>
                {/each}
              </ul>
            {/if}
          </div>
        </aside>

        <!-- MAIN SEARCH & DOCUMENTS LIST (3/4 layout, fluid & responsive) -->
        <section class="lg:col-span-3 space-y-6" aria-label="Поиск и список материалов">

          <!-- Search Input Box (Vastly interactive) -->
          <div class="relative cyan-glow-focus transition-all">
            <div class="absolute inset-y-0 left-4 flex items-center pointer-events-none text-on-surface-variant">
              <span class="material-symbols-outlined text-lg">search</span>
            </div>

            <input
              type="text"
              bind:value={q}
              on:input={handleSearchInput}
              on:focus={() => showSuggestions = q.length > 0}
              on:blur={() => setTimeout(() => showSuggestions = false, 200)}
              class="w-full bg-surface-container-lowest border-0 border-b-2 border-outline-variant text-[#d4e4fa] py-4 pl-12 pr-12 focus:ring-0 focus:border-primary font-body-lg text-base transition-all placeholder-on-surface-variant"
              placeholder="Поиск по ключевым словам (в т.ч. ФБУН, ГЭК, ГИА, ФГОС)..."
            />

            {#if q}
              <button
                on:click={() => { q = ""; handleSearchInput(); }}
                class="absolute inset-y-0 right-4 flex items-center text-on-surface-variant hover:text-primary"
                aria-label="Очистить поиск"
              >
                <span class="material-symbols-outlined text-base">close</span>
              </button>
            {/if}

            <!-- Search Autocomplete Suggestion Dropdown (Zero CLS, absolute container) -->
            {#if showSuggestions && searchSuggestions.length > 0}
              <div class="absolute left-0 w-full bg-surface-container-high border border-outline-variant rounded-sm mt-1 z-30 shadow-2xl max-h-60 overflow-y-auto">
                <ul class="py-1">
                  {#each searchSuggestions as sug}
                    <li>
                      <button
                        on:click={() => selectSuggestion(sug)}
                        class="w-full text-left px-4 py-2 text-sm text-[#d4e4fa] hover:bg-primary-container hover:text-on-primary-fixed font-semibold transition-colors flex items-center gap-2"
                      >
                        <span class="material-symbols-outlined text-xs text-primary">history</span>
                        {sug}
                      </button>
                    </li>
                  {/each}
                </ul>
              </div>
            {/if}
          </div>

          <!-- Synonym Explainer Tip Banner (Dynamic and highly helpful) -->
          <div class="bg-surface-container-low border-l-2 border-primary p-3 rounded-r text-xs flex gap-2 items-start">
            <span class="material-symbols-outlined text-primary text-sm mt-0.5">info</span>
            <div>
              <p class="font-bold text-[#d4e4fa] mb-0.5">Интеллектуальный поиск активен</p>
              <p class="text-on-surface-variant">Поиск автоматически учитывает официальные аббревиатуры учебного центра: <span class="text-primary font-bold">ФГОС</span>, <span class="text-primary font-bold">ГИА</span>, <span class="text-primary font-bold">ГЭК</span>, <span class="text-primary font-bold">ФБУН</span> и их синонимы.</p>
            </div>
          </div>

          <!-- Tab Navigation (Desktop-responsive + Touch Friendly >44px height) -->
          <div class="flex border-b border-outline-variant">
            <button
              on:click={() => activeTab = "search"}
              class="px-6 py-3 font-semibold text-sm transition-all border-b-2 flex items-center gap-2 min-h-[44px]"
              class:border-primary={activeTab === "search"}
              class:text-primary={activeTab === "search"}
              class:border-transparent={activeTab !== "search"}
              class:text-on-surface-variant={activeTab !== "search"}
            >
              <span class="material-symbols-outlined text-base">folder_open</span>
              Все материалы ({documents.length})
            </button>
            <button
              on:click={() => activeTab = "library"}
              class="px-6 py-3 font-semibold text-sm transition-all border-b-2 flex items-center gap-2 min-h-[44px]"
              class:border-primary={activeTab === "library"}
              class:text-primary={activeTab === "library"}
              class:border-transparent={activeTab !== "library"}
              class:text-on-surface-variant={activeTab !== "library"}
            >
              <span class="material-symbols-outlined text-base">star</span>
              Избранные ({favorites.length})
            </button>
          </div>

          <!-- Document Tag Filter Chips -->
          <div class="flex flex-wrap gap-2 py-1">
            {#each ["ординатура", "аспирантура", "шаблоны", "нормативные акты", "вопросы к экзаменам", "педиатрия", "инструкции", "регламент"] as tag}
              <button
                on:click={() => selectedTag = (selectedTag === tag ? "" : tag)}
                class="px-3 py-1.5 rounded-full text-xs font-semibold transition-colors duration-100 flex items-center gap-1.5 border min-h-[32px]"
                class:bg-primary-container={selectedTag === tag}
                class:text-on-primary-fixed={selectedTag === tag}
                class:border-primary={selectedTag === tag}
                class:bg-surface-container={selectedTag !== tag}
                class:text-on-surface-variant={selectedTag !== tag}
                class:border-outline-variant={selectedTag !== tag}
              >
                <span>#{tag}</span>
                {#if selectedTag === tag}
                  <span class="material-symbols-outlined text-xs">close</span>
                {/if}
              </button>
            {/each}
          </div>

          <!-- Documents List (Dynamic rendering) -->
          {#if filteredDocuments.length === 0}
            <div class="bg-surface-container border border-outline-variant p-12 text-center rounded space-y-4">
              <span class="material-symbols-outlined text-5xl text-on-surface-variant" data-icon="search_off">search_off</span>
              <p class="text-base font-semibold text-[#d4e4fa]">Документов по вашему запросу не найдено</p>
              <p class="text-sm text-on-surface-variant">Попробуйте изменить формулировку или сбросить active-фильтры.</p>
            </div>
          {:else}
            <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
              {#each filteredDocuments as doc}
                <div
                  class="bg-surface-container border border-outline-variant p-5 rounded group hover:bg-surface-container-high hover:border-primary transition-all duration-200 flex flex-col justify-between focus-within:ring-2 focus-within:ring-primary outline-none relative"
                >
                  <!-- Semantic absolute-positioned overlay button for keyboard focus & detail triggers -->
                  <button
                    on:click={() => openDocumentDetails(doc)}
                    class="absolute inset-0 w-full h-full cursor-pointer bg-transparent border-0 outline-none rounded z-10"
                    aria-label="Просмотреть подробности документа {doc.name}"
                  ></button>

                  <div class="space-y-3 relative z-20 pointer-events-none flex flex-col flex-1 justify-between">
                    <div>
                      <div class="flex justify-between items-start gap-3">
                        <h4 class="font-headline-md text-base md:text-lg text-[#d4e4fa] group-hover:text-primary transition-colors font-bold line-clamp-2">
                          {doc.name}
                        </h4>
                        <button
                          on:click|stopPropagation={() => toggleFavorite(doc.id)}
                          class="text-on-surface-variant hover:text-yellow-400 transition-colors pointer-events-auto relative z-30"
                          aria-label={favorites.includes(doc.id) ? "Удалить из избранного" : "Добавить в избранное"}
                        >
                          <span class="material-symbols-outlined text-xl" class:text-yellow-400={favorites.includes(doc.id)}>
                            {favorites.includes(doc.id) ? 'star' : 'star_border'}
                          </span>
                        </button>
                      </div>

                      <p class="text-xs text-on-surface-variant line-clamp-3 leading-relaxed mt-3">
                        {doc.description}
                      </p>
                    </div>

                    <div class="pt-4 space-y-3 border-t border-outline-variant/50 mt-4">
                      <!-- Tags -->
                      <div class="flex flex-wrap gap-1">
                        {#each doc.tags as t}
                          <span class="text-[10px] font-bold uppercase tracking-wider bg-surface-variant text-secondary px-1.5 py-0.5 rounded-sm">
                            {t}
                          </span>
                        {/each}
                      </div>

                      <!-- Meta specs -->
                      <div class="flex justify-between items-center text-[11px] text-[#869397] font-semibold">
                        <span class="flex items-center gap-1">
                          <span class="material-symbols-outlined text-xs">history</span>
                          v{doc.version}
                        </span>
                        <span>{getEduLevelLabel(doc.edu_level)}</span>
                      </div>
                    </div>
                  </div>
                </div>
              {/each}
            </div>
          {/if}

        </section>

      </div>
    {/if}

  </main>

  <!-- DOCUMENT DETAILS SLIDE-OVER DRAWER (Strictly Russian, responsive, and robust) -->
  {#if selectedDocument}
    <div class="fixed inset-0 bg-[#051424]/80 backdrop-blur-sm z-50 flex justify-end" role="dialog" aria-modal="true" aria-labelledby="drawer-title">
      <!-- Backdrop click closer -->
      <button
        on:click={() => selectedDocument = null}
        class="absolute inset-0 w-full h-full bg-transparent cursor-default outline-none"
        tabindex="-1"
        aria-label="Закрыть панель деталей"
      ></button>

      <!-- Drawer Content (Optimized sizing, 100% responsive: occupies full screen on mobile, 600px on desktop) -->
      <div class="relative w-full max-w-[640px] h-full bg-surface-container border-l border-outline-variant p-6 md:p-8 overflow-y-auto flex flex-col justify-between shadow-2xl z-10">

        <!-- Header -->
        <div class="space-y-4">
          <div class="flex justify-between items-start gap-4 border-b border-outline-variant pb-4">
            <div class="space-y-1">
              <span class="text-xs font-bold uppercase text-primary tracking-widest">
                {getDocTypeLabel(selectedDocument.doc_type)}
              </span>
              <h3 id="drawer-title" class="text-lg md:text-xl font-bold text-[#d4e4fa]">
                {selectedDocument.name}
              </h3>
            </div>

            <div class="flex items-center gap-2">
              {#if user && !accessDeniedToSelected}
                <button
                  on:click={handleEditClick}
                  class="text-[#1A365D] hover:text-opacity-80 transition-colors p-1 flex items-center gap-1 text-xs font-semibold bg-[#e0e0ff] rounded px-2 py-1 min-h-[44px]"
                  aria-label="Редактировать"
                  id="edit-doc-btn"
                >
                  <span class="material-symbols-outlined text-sm">edit</span>
                  <span>Редактировать</span>
                </button>
              {/if}

              <button
                on:click={() => selectedDocument = null}
                class="text-on-surface-variant hover:text-primary transition-colors p-1 min-h-[44px]"
                aria-label="Закрыть"
              >
                <span class="material-symbols-outlined text-2xl">close</span>
              </button>
            </div>
          </div>

          {#if editError}
            <div class="bg-red-950 bg-opacity-40 border border-red-500 text-red-200 p-3 rounded text-sm font-medium" id="edit-error-msg">
              {editError}
            </div>
          {/if}
          {#if editSuccess}
            <div class="bg-green-950 bg-opacity-40 border border-green-500 text-green-200 p-3 rounded text-sm font-medium" id="edit-success-msg">
              {editSuccess}
            </div>
          {/if}

          {#if accessDeniedToSelected}
            <!-- STUDENT BUDGET ACCESS DENIED -->
            <div class="p-6 bg-red-950/40 border border-red-500 rounded text-center space-y-4" id="budget-access-denied">
              <span class="material-symbols-outlined text-red-500 text-5xl">lock</span>
              <h4 class="text-lg font-bold text-red-200">Доступ ограничен</h4>
              <p class="text-sm text-red-300 leading-relaxed">
                У ординаторов, аспирантов и слушателей нет прав доступа к финансовым и бюджетным документам центра.
              </p>
            </div>
          {:else}
            {#if isEditing}
              <div class="space-y-4 border border-outline-variant p-4 bg-[#051424] rounded">
                <h4 class="font-bold text-sm text-[#d4e4fa]">Редактирование материала</h4>
                <div>
                  <label for="edit-name-input" class="block text-xs font-bold text-[#d4e4fa] uppercase tracking-wider mb-2">Название документа</label>
                  <input
                    id="edit-name-input"
                    type="text"
                    bind:value={editName}
                    class="w-full bg-surface-container-lowest border border-outline-variant rounded p-3 text-sm text-[#051424] focus:border-primary focus:ring-1 focus:ring-primary"
                    placeholder="Введите новое название..."
                  />
                </div>
                <div>
                  <label for="edit-description-input" class="block text-xs font-bold text-[#d4e4fa] uppercase tracking-wider mb-2">Аннотация</label>
                  <textarea
                    id="edit-description-input"
                    bind:value={editDescription}
                    class="w-full bg-surface-container-lowest border border-outline-variant rounded p-3 text-sm text-[#051424] focus:border-primary focus:ring-1 focus:ring-primary"
                    rows="4"
                    placeholder="Введите новое описание..."
                  ></textarea>
                </div>
                <div class="flex gap-2">
                  <button
                    on:click={handleSaveEdit}
                    class="flex-1 py-2 bg-[#1A365D] text-[#e0e0ff] font-bold text-xs rounded hover:bg-opacity-90 transition-all min-h-[44px]"
                    id="save-edit-btn"
                  >
                    Сохранить изменения
                  </button>
                  <button
                    on:click={handleCancelEdit}
                    class="flex-1 py-2 bg-surface-variant hover:bg-surface-container-highest text-[#d4e4fa] font-bold text-xs rounded transition-colors min-h-[44px]"
                    id="cancel-edit-btn"
                  >
                    Отмена
                  </button>
                </div>
              </div>
            {:else}
              <!-- Document Profile Specs -->
              <div class="grid grid-cols-2 gap-4 bg-surface-container-low p-4 border border-outline-variant rounded text-xs">
                <div>
                  <span class="block text-on-surface-variant uppercase font-bold text-[10px] mb-0.5">Специальность</span>
                  <span class="font-semibold text-[#d4e4fa]">{getSpecialtyLabel(selectedDocument.specialty)}</span>
                </div>
                <div>
                  <span class="block text-on-surface-variant uppercase font-bold text-[10px] mb-0.5">Уровень образования</span>
                  <span class="font-semibold text-[#d4e4fa]">{getEduLevelLabel(selectedDocument.edu_level)}</span>
                </div>
                <div>
                  <span class="block text-on-surface-variant uppercase font-bold text-[10px] mb-0.5">Размер файла</span>
                  <span class="font-semibold text-[#d4e4fa]">{formatBytes(selectedDocument.fileSize)}</span>
                </div>
                <div>
                  <span class="block text-on-surface-variant uppercase font-bold text-[10px] mb-0.5">Дата обновления</span>
                  <span class="font-semibold text-[#d4e4fa]">{formatDate(selectedDocument.updatedAt)}</span>
                </div>
                <div>
                  <span class="block text-on-surface-variant uppercase font-bold text-[10px] mb-0.5">Текущая версия</span>
                  <span class="font-semibold text-[#d4e4fa]">Версия {selectedDocument.version}</span>
                </div>
                <div>
                  <span class="block text-on-surface-variant uppercase font-bold text-[10px] mb-0.5">Изменил</span>
                  <span class="font-semibold text-[#d4e4fa]">{selectedDocument.updatedBy}</span>
                </div>
              </div>

              <!-- Description -->
              <div class="space-y-2">
                <h4 class="font-bold text-sm text-[#d4e4fa]">Аннотация документа</h4>
                <p class="text-xs text-on-surface-variant leading-relaxed">
                  {selectedDocument.description}
                </p>
              </div>

              <!-- Version History Section -->
              <div class="space-y-2">
                <h4 class="font-bold text-sm text-[#d4e4fa] flex items-center gap-1.5">
                  <span class="material-symbols-outlined text-sm">history</span>
                  История версий документа
                </h4>
                <div class="border border-outline-variant rounded overflow-hidden">
                  <table class="w-full text-left border-collapse text-xs">
                    <thead>
                      <tr class="bg-surface-container-low border-b border-outline-variant font-bold text-on-surface-variant">
                        <th class="p-2">Версия</th>
                        <th class="p-2">Дата изменения</th>
                        <th class="p-2">Автор изменений</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr class="border-b border-outline-variant/30">
                        <td class="p-2 font-bold text-[#d4e4fa]">v{selectedDocument.version} (Текущая)</td>
                        <td class="p-2">{formatDate(selectedDocument.updatedAt)}</td>
                        <td class="p-2">{selectedDocument.updatedBy}</td>
                      </tr>
                      {#if selectedDocument.version > 1}
                        <tr class="border-b border-outline-variant/30 bg-surface-container-low/20">
                          <td class="p-2">v{selectedDocument.version - 1}</td>
                          <td class="p-2">12 июня 2026</td>
                          <td class="p-2">system@epidem.ru</td>
                        </tr>
                      {/if}
                    </tbody>
                  </table>
                </div>
              </div>
            {/if}

            {#if !isEditing}
              <!-- Comments Section -->
              <div class="space-y-3 pt-2">
                <h4 class="font-bold text-sm text-[#d4e4fa] flex items-center gap-1.5">
                  <span class="material-symbols-outlined text-sm">comment</span>
                  Комментарии и обсуждение
                </h4>

                <div class="space-y-3 max-h-48 overflow-y-auto pr-1">
                  {#if !(commentsDb[selectedDocument.id]) || commentsDb[selectedDocument.id].length === 0}
                    <p class="text-xs text-on-surface-variant py-2">Нет комментариев к этому документу. Напишите первый!</p>
                  {:else}
                    {#each commentsDb[selectedDocument.id] as comment}
                      <div class="p-3 bg-surface-container-low border border-outline-variant/60 rounded space-y-1">
                        <div class="flex justify-between text-[11px] font-semibold">
                          <span class="text-primary">{comment.user}</span>
                          <span class="text-on-surface-variant">{formatDate(comment.createdAt)}</span>
                        </div>
                        <p class="text-xs text-on-surface">{comment.text}</p>
                      </div>
                    {/each}
                  {/if}
                </div>

                <!-- Add Comment Form -->
                {#if user}
                  <div class="space-y-2 pt-1">
                    <textarea
                      bind:value={newCommentText}
                      disabled={isOffline}
                      placeholder={isOffline ? "Вы не можете комментировать в автономном режиме" : "Напишите ваш комментарий или вопрос по материалу..."}
                      class="w-full bg-surface-container-lowest border border-outline-variant rounded p-2 text-xs text-on-surface focus:border-primary focus:ring-0"
                      rows="2"
                    ></textarea>
                    <div class="flex justify-end">
                      <button
                        on:click={postComment}
                        disabled={!newCommentText || isOffline}
                        class="px-4 py-2 bg-primary text-on-primary-fixed hover:bg-opacity-90 disabled:opacity-50 text-xs font-bold rounded flex items-center gap-1.5 transition-all"
                      >
                        <span class="material-symbols-outlined text-xs">send</span>
                        <span>Отправить</span>
                      </button>
                    </div>
                  </div>
                {/if}
              </div>

              <!-- Document Actualization Request Box (Only for Teachers/Students to report outdated documents) -->
              <div class="border-t border-outline-variant pt-4 space-y-3">
                <h4 class="font-bold text-sm text-[#d4e4fa] flex items-center gap-1.5">
                  <span class="material-symbols-outlined text-sm text-red-400">report_problem</span>
                  Сообщить о неактуальности материала
                </h4>
                <p class="text-xs text-on-surface-variant leading-normal">
                  Если данный документ устарел или противоречит приказам Минздрава/Роспотребнадзора, отправьте official-запрос на актуализацию.
                </p>

                {#if actualizationSuccess}
                  <div class="bg-green-950 bg-opacity-30 border border-green-500 text-green-200 p-3 rounded text-xs font-semibold">
                    Запрос на актуализацию успешно отправлен контент-менеджерам центра!
                  </div>
                {:else}
                  <div class="space-y-2">
                    <input
                      type="text"
                      bind:value={actualizationReason}
                      disabled={isOffline}
                      placeholder={isOffline ? "Недоступно в автономном режиме" : "Укажите причину актуализации (например, Приказ №124)..."}
                      class="w-full bg-surface-container-lowest border border-outline-variant rounded p-2 text-xs focus:border-primary focus:ring-0 text-on-surface"
                    />
                    <button
                      on:click={sendActualizationRequest}
                      disabled={!actualizationReason || isOffline}
                      class="w-full py-2 bg-error-container text-on-error-container hover:bg-opacity-85 disabled:opacity-50 text-xs font-bold rounded transition-colors"
                    >
                      Отправить запрос на актуализацию
                    </button>
                  </div>
                {/if}
              </div>
            {/if}
          {/if}

        </div>

        <!-- Document Download Footer -->
        {#if !accessDeniedToSelected}
          <div class="border-t border-outline-variant pt-4 flex gap-3">
            <a
              href="/api/v1/documents/{selectedDocument.id}/export?format=pdf"
              on:click|preventDefault={() => alert('Скачивание PDF начато (эмуляция)...')}
              class="flex-1 py-3 bg-primary text-on-primary-fixed text-center font-bold text-xs rounded hover:bg-opacity-90 transition-colors flex items-center justify-center gap-2 min-h-[44px]"
              id="download-pdf-btn"
            >
              <span class="material-symbols-outlined text-sm">download</span>
              Скачать PDF
            </a>
            <a
              href="/api/v1/documents/{selectedDocument.id}/export?format=docx"
              on:click|preventDefault={() => alert('Скачивание DOCX начато (эмуляция)...')}
              class="flex-1 py-3 bg-surface-variant hover:bg-surface-container-highest text-[#d4e4fa] text-center font-bold text-xs rounded transition-colors flex items-center justify-center gap-2 min-h-[44px]"
              id="download-docx-btn"
            >
              <span class="material-symbols-outlined text-sm">description</span>
              Скачать DOCX
            </a>
          </div>
        {/if}

      </div>
    </div>
  {/if}

  <!-- BOTTOM NAVIGATION BAR (Strictly matching the layout of the mockup, visible on mobile) -->
  {#if user}
    <nav class="fixed bottom-0 left-0 w-full h-16 flex justify-around items-center border-t border-outline-variant bg-surface-container/95 backdrop-blur-md z-40 pb-safe">
      <button
        on:click={() => activeTab = "search"}
        class="flex flex-col items-center justify-center transition-colors flex-1 h-full font-semibold text-xs animate-none"
        class:text-primary={activeTab === "search"}
        class:text-on-surface-variant={activeTab !== "search"}
      >
        <span class="material-symbols-outlined text-xl">search</span>
        <span>Поиск</span>
      </button>

      <button
        on:click={() => activeTab = "library"}
        class="flex flex-col items-center justify-center transition-colors flex-1 h-full font-semibold text-xs animate-none"
        class:text-primary={activeTab === "library"}
        class:text-[#869397]={activeTab !== "library"}
      >
        <span class="material-symbols-outlined text-xl">star</span>
        <span>Избранное</span>
      </button>
    </nav>
  {/if}

</div>
